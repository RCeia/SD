package downloader;

import adaptivestopwords.IAdaptiveStopWords;
import common.PageData;
import common.RetryLogic;
import multicast.ReliableMulticast;
import queue.IQueue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import barrel.IBarrel;
import java.util.ArrayList;
import java.net.InetAddress;

public class Downloader implements IDownloader {
    private static int nextId = 1;
    final int id;
    private final String registryHost;
    private final int registryPort;

    private IQueue queue;
    private List<IBarrel> barrels = new ArrayList<>();
    private ReliableMulticast multicast;
    private final Object barrelLock = new Object();

    //                 Campos para o algoritmo stop words
    // ======================================================================
    private IAdaptiveStopWords adaptiveStopWords;
    private final Tokenizer tokenizer = new Tokenizer();
    // Limiar de 70% para uma palavra ser considerada stop word
    private static final double STOPWORD_THRESHOLD = 0.7;
    // Cache local com as stopwods para evitar chamadas RMI a cada download
    private Set<String> localStopWordsCache = new HashSet<>();
    private int downloadCountSinceCacheUpdate = 0;
    // ======================================================================

    public Downloader(IQueue queue, String registryHost, int registryPort) {
        super();
        this.queue = queue;
        this.registryHost = registryHost;
        this.registryPort = registryPort;
        this.id = (int) (ProcessHandle.current().pid() * 10 + nextId++);
        // Samu: Renomeei para discoverServices por que agora não basta que o downloader verifique se tem barrels ativos.
        //       Agora também tem de verificar que o serviço que identifica as stop words está a correr.
        discoverServices(); // tenta encontrar barrels logo ao iniciar
        this.multicast = new ReliableMulticast(3, 2000, 10, 2);
    }

    @Override
    public void takeURL(String url) throws RemoteException {
        synchronized (barrelLock) {
            while (barrels.isEmpty()) {
                System.out.println("[Downloader" + id + "] - Nenhum barrel ativo. Aguardando...");
                try {
                    barrelLock.wait();
                } catch (InterruptedException ignored) {}
            }
        }

        System.out.println("[Downloader" + id + "] - Recebi URL para download: " + url);
        download(url);
    }

    private void safeAddURL(String url) {
        try {
            RetryLogic.executeWithRetry(3, 2000, this::reconnectQueue,
                    () -> {
                        queue.addURL(url);
                        return null;
                    }
            );
        } catch (Exception e) {
            System.err.println("[Downloader" + id + "] - Falha permanente ao adicionar URL à Queue: " + e.getMessage());
        }
    }

    private void safeAddURLs(List<String> urls) {
        try {
            RetryLogic.executeWithRetry(3, 2000, this::reconnectQueue,
                    () -> {
                        queue.addURLs(urls);
                        return null;
                    }
            );
        } catch (Exception e) {
            System.err.println("[Downloader" + id + "] - Falha permanente ao adicionar múltiplos URLs à Queue: " + e.getMessage());
        }
    }

    private void sendToBarrels(PageData data) throws RemoteException {
        if (barrels.isEmpty()) {
            System.err.println("[Downloader" + id + "] - Nenhum Barrel ativo disponível. URL será re-adicionado à Queue.");
            safeAddURL(data.getUrl());
            return;
        }

        System.out.println("\n[Downloader" + id + "] - A enviar página para os Barrels (via multicast lógico)...");
        System.out.println("URL: " + data.getUrl());
        System.out.println("Título: " + data.getTitle());
        System.out.println("Palavras: " + data.getWords().size());
        System.out.println("Links encontrados: " + data.getOutgoingLinks().size());

        List<IBarrel> failedBarrels = multicast.multicastToBarrels(barrels, data);

        if (!failedBarrels.isEmpty()) {
            barrels.removeAll(failedBarrels);
            System.err.println("[Downloader" + id + "] - Removidos " + failedBarrels.size()
                    + " barrels inativos da lista. Barrels ativos: " + barrels.size());
        }
    }

    /**
     * AQUI estava um bug: estavas a fazer sempre getRegistry(registryHost, 1099)
     * mesmo que o servidor esteja noutra porta.
     */
    private boolean reconnectQueue() {
        try {
            System.out.println("[Downloader" + id + "] - Tentando reconectar à Queue...");
            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
            IQueue newQueue = (IQueue) registry.lookup("URLQueueInterface");
            this.queue = newQueue;
            System.out.println("[Downloader" + id + "] - Reconectado à Queue com sucesso!");
            return true;
        } catch (Exception e) {
            System.err.println("[Downloader" + id + "] - Falha ao reconectar à Queue: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void notifyFinished() throws RemoteException {
        System.out.println("[Downloader" + id + "] - Download finished, notifying Queue...");

        try {
            RetryLogic.executeWithRetry(3, 2000, this::reconnectQueue,
                    () -> {
                        queue.notifyDownloaderAvailable(this);
                        return null;
                    }
            );
        } catch (Exception e) {
            System.err.println("[Downloader" + id + "] - Falha permanente ao notificar Queue: " + e.getMessage());
        }
    }

    private void download(String url) {
        new Thread(() -> {
            // Flag para decidir se o URL deve ser re-adicionado à Queue em caso de falha.
            boolean reAddUrlToQueue = false;

            try {
                // 1. Validação de extensão
                if (url.matches("(?i).*\\.(pdf|jpg|jpeg|png|gif|mp4|zip|rar|docx|xlsx|pptx|mp3)$")) {
                    System.out.println("[Downloader" + id + "] - URL não possui o formato permitido: " + url);
                    return;
                }

                // 2. Verificação de URL já visitado no Barrel
                if (!barrels.isEmpty()) {
                    try {
                        boolean alreadyVisited = barrels.getFirst().isUrlInBarrel(url);
                        if (alreadyVisited) {
                            System.out.println("[Downloader" + id + "] - URL já visitado anteriormente: " + url);
                            return;
                        }
                    } catch (Exception e) {
                        System.err.println("[Downloader" + id + "] - Erro ao contactar o Barrel: " + e.getMessage());
                        reAddUrlToQueue = true; // Barrel falhou, marcar para re-adicionar
                        return;
                    }
                }

                // 3. Conexão HTTP e Validação de Content-Type
                System.out.println("[Downloader" + id + "] - Tentando conectar: " + url);

                // Ajuste a conexão para usar execute() para checar o Content-Type
                org.jsoup.Connection.Response response = Jsoup.connect(url)
                        .timeout(5000)
                        .ignoreHttpErrors(true)
                        .execute();

                // --- NOVA VALIDAÇÃO HTTP STATUS CODE (Falha Permanente) ---
                int statusCode = response.statusCode();

                // 4xx são geralmente erros permanentes (404, 403, 410). Excluímos 429 (Too Many Requests) que é temporário.
                if (statusCode >= 400 && statusCode < 500 && statusCode != 429) {
                    System.out.println("[Downloader" + id + "] - Falha Permanente HTTP (" + statusCode + "). Descartado: " + url);
                    return; // Não re-adiciona, vai para o finally
                }


                String contentType = response.contentType();

                // Validação de Content-Type
                if (contentType == null || !contentType.contains("text/html")) {
                    System.out.println("[Downloader" + id + "] - URL ignorado por tipo de conteúdo inválido (" + contentType + "): " + url);
                    return;
                }

                // 4. Processamento do Documento
                System.out.println("[Downloader" + id + "] - Downloading: " + url);
                Document doc = response.parse();

                String title = doc.title();
                String text = doc.body().text();

                if (text.trim().isEmpty()) {
                    System.out.println("[Downloader" + id + "] - Página vazia ignorada: " + url);
                    return;
                }


                // Tokenizar
                List<String> allWords = tokenizer.tokenize(text);
                if (allWords.isEmpty()) {
                    System.out.println("[Downloader" + id +"] - A página " + url + " não contém palavras após tokenização.");
                    notifyFinished();
                    return;
                }

                // Enviar palavras para o algoritmo de aprendizagem de stop words
                Set<String> uniqueWords = new HashSet<>(allWords);
                adaptiveStopWords.processDoc(url, uniqueWords);

                // Atualizar cache de stop words
                if (downloadCountSinceCacheUpdate++ % 10 == 0) {
                    System.out.println("[Downloader" + id + "] - A atualizar cache de stop words...");
                    localStopWordsCache = adaptiveStopWords.getStopWords(STOPWORD_THRESHOLD);
                    System.out.println("[Downloader" + id + "] - Cache atualizado. " + localStopWordsCache.size() + " stop words.");
                }

                // Filtrar as palavras
                List<String> filteredWords = allWords.stream()
                        .filter(word -> !localStopWordsCache.contains(word))
                        .collect(Collectors.toList());

                // Extrair links
                Elements links = doc.select("a[href]");
                List<String> outgoingLinks = links.stream()
                        .map(link -> link.absUrl("href"))
                        .filter(href -> !href.isEmpty())
                        .collect(Collectors.toList());

                // Enviar para os barrels apenas as palavras filtradas
                PageData pageData = new PageData(url, title, filteredWords, outgoingLinks);

                // 5. Envio para Barrels e tratamento de links
                sendToBarrels(pageData);

                if (outgoingLinks.size() == 1) {
                    safeAddURL(outgoingLinks.getFirst());
                    System.out.println("[Downloader" + id + "] - Adicionado 1 novo URL à Queue.");
                } else if (!outgoingLinks.isEmpty()) {
                    safeAddURLs(outgoingLinks);
                    System.out.println("[Downloader" + id + "] - Adicionados " + outgoingLinks.size() + " novos URLs à Queue.");
                }

            } catch (Exception e) {
                // Assume que NUNCA deve ser repetido (para quebrar o loop infinito)
                reAddUrlToQueue = false;

                // A ÚNICA exceção que consideramos temporária é o Timeout.
                if (e.getCause() instanceof java.net.SocketTimeoutException ||
                        e instanceof java.net.SocketTimeoutException) {

                    // Timeout: Vale a pena repetir.
                    reAddUrlToQueue = true;
                    System.err.println("[Downloader" + id + "] - Falha Temporária (Timeout). Será re-adicionado: " + url);

                } else {
                    // Qualquer outra exceção (incluindo DNS, que falha aqui): Descartado.
                    System.err.println("[Downloader" + id + "] - Falha Permanente/Inesperada. Descartado: " + url + ". Causa: " + e.getMessage());
                }

            } finally {
                // O bloco FINALLY É SEMPRE EXECUTADO.

                if (reAddUrlToQueue) {
                    // Tenta re-adicionar o URL que o bloco 'catch' marcou para repetição.
                    try {
                        safeAddURL(url);
                        System.out.println("[Downloader" + id + "] - URL re-adicionado à Queue.");
                    } catch (Exception ex) {
                        System.err.println("[Downloader" + id + "] - FALHA CRÍTICA ao re-adicionar URL. " + ex.getMessage());
                    }
                }

                // Notifica a Queue para obter o próximo URL
                try {
                    notifyFinished();
                    System.out.println("[Downloader" + id + "] - Notificado Queue: Downloader Disponível.");
                } catch (Exception ex) {
                    System.err.println("[Downloader" + id + "] - FALHA CRÍTICA ao notificar a Queue. " + ex.getMessage());
                }
            }
        }).start();
    }

    private void discoverServices() {
        try {
            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
            String[] boundNames = registry.list();

            List<IBarrel> discovered = new ArrayList<>();

            for (String bound : boundNames) {
                if (bound.startsWith("Barrel")) {
                    try {
                        IBarrel barrel = (IBarrel) registry.lookup(bound);
                        if (barrel.isActive()) {
                            discovered.add(barrel);
                            System.out.println("[Downloader" + id + "] - Ligado a " + bound + " (ativo).");
                        } else {
                            System.out.println("[Downloader" + id + "] - Ignorado " + bound + " (inativo).");
                        }
                    } catch (RemoteException e) {
                        System.err.println("[Downloader" + id + "] - Falha ao contactar " + bound + ": " + e.getMessage());
                    }
                } else if (bound.equals("AdaptiveStopWords")) { // Procura o serviço do algoritmo stop words.
                    adaptiveStopWords = (IAdaptiveStopWords) registry.lookup("AdaptiveStopWords");
                    System.out.println("[Downloader" + id + "] - Ligado a AdaptiveStopWords.");
                }
            }

            barrels = discovered;

            if (barrels.isEmpty()) {
                System.out.println("[Downloader" + id + "] - Nenhum Barrel ativo encontrado. Aguardando callback...");
            } else {
                System.out.println("[Downloader" + id + "] - Barrels ativos encontrados: " + barrels.size());
            }

            if (adaptiveStopWords == null) {
                System.out.println("[Downloader" + id + "] - O algortimo AdaptiveStopWords não foi encontrado. Aguardando callback...");

            } else {
                System.out.println("[Downloader" + id + "] - O algoritmo AdaptiveStopWords foi encontrado.");
            }

        } catch (Exception e) {
            System.err.println("[Downloader" + id + "] - Erro na descoberta de Barrels: " + e.getMessage());
        }
    }

    @Override
    public void addBarrel(IBarrel newBarrel) throws RemoteException {
        synchronized (barrelLock) {
            try {
                if (newBarrel.isActive() && !barrels.contains(newBarrel)) {
                    barrels.add(newBarrel);
                    System.out.println("[Downloader" + id + "] - Novo barrel ativo registado dinamicamente: " + newBarrel);
                    barrelLock.notifyAll();
                } else if (!newBarrel.isActive()) {
                    System.out.println("[Downloader" + id + "] - Barrel recebido mas ainda inativo: " + newBarrel);
                }
            } catch (Exception e) {
                System.err.println("[Downloader" + id + "] - Erro ao validar barrel no callback: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        try {
            // args:
            // 0 -> IP do servidor (onde está a Queue)
            // 1 -> porta do registry do servidor
            // 2 -> (opcional) IP desta máquina para o RMI anunciar
            String serverIP = args.length > 0 ? args[0] : "localhost";
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

            // ESTE é o IP que o SERVIDOR vai usar para chegar AQUI
            String myPublicIP;
            if (args.length > 2) {
                myPublicIP = args[2];
            } else {
                // último recurso: tenta descobrir
                myPublicIP = InetAddress.getLocalHost().getHostAddress();
            }

            System.setProperty("java.rmi.server.hostname", myPublicIP);
            System.out.println("[INFO] RMI hostname definido como: " + myPublicIP);

            // liga ao registry remoto (o da Queue)
            Registry registry = LocateRegistry.getRegistry(serverIP, port);
            IQueue queue = (IQueue) registry.lookup("URLQueueInterface");

            Downloader downloader = new Downloader(queue, serverIP, port);
            IDownloader stub = (IDownloader) UnicastRemoteObject.exportObject(downloader, 0);

            String downloaderName = "Downloader" + downloader.id;
            // regista o callback deste downloader no registry do servidor
            registry.rebind(downloaderName, stub);

            System.out.println("[Downloader" + downloader.id + "] - Registado no RMI Registry remoto como '" + downloaderName + "'.");

            // regista-se também na queue
            queue.registerDownloader(stub, downloader.id);
            System.out.println("[Downloader" + downloader.id + "] - Registado na Queue e pronto para receber URLs.");

            synchronized (Downloader.class) {
                Downloader.class.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
