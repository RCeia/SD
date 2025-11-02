package downloader;

import common.PageData;
import common.RetryLogic;
import multicast.ReliableMulticast;
import queue.IQueue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.List;
import java.util.stream.Collectors;
import barrel.IBarrel;
import java.util.ArrayList;


public class Downloader implements IDownloader {
    private static int nextId = 1;
    private final int id;
    private final String registryHost;
    private final int registryPort;

    private IQueue queue;
    private List<IBarrel> barrels = new ArrayList<>();
    private ReliableMulticast multicast;
    private final Object barrelLock = new Object();

    public Downloader(IQueue queue, String registryHost, int registryPort) {
        super();
        this.queue = queue;
        this.registryHost = registryHost;
        this.registryPort = registryPort;
        this.id = (int) (ProcessHandle.current().pid() * 10 + nextId++);
        discoverBarrels(); // Ver se já existem barrels quando o downlaoder é iniciado
        this.multicast = new ReliableMulticast(3, 2000, 10, 2); // 3 tentativas, 2000ms de timeout, 10 threads, fator de backoff 2
    }

    @Override
    public void takeURL(String url) throws RemoteException {
        synchronized (barrelLock) {
            while (barrels.isEmpty()) {
                System.out.println("[Downloader" + id + "] - Nenhum barrel ativo. Aguardando...");
                try {
                    barrelLock.wait(); // Espera até ser notificado de um barrel novo
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
        // Verifica quais barrels ainda estão ativos


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

        if (barrels.isEmpty()) {
            System.err.println("[Downloader" + id + "] - Todos os barrels estão inativos!");
        }
    }

    private boolean reconnectQueue() {
        try {
            System.out.println("[Downloader" + id + "] - Tentando reconectar à Queue...");
            Registry registry = LocateRegistry.getRegistry(registryHost, 1099);
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
            RetryLogic.executeWithRetry(3, 2000, () ->reconnectQueue(),
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
            try {

                if (url.matches("(?i).*\\.(pdf|jpg|jpeg|png|gif|mp4|zip|rar|docx|xlsx|pptx|mp3)$")) {
                    System.out.println("[Downloader" + id + "] - URL não possui o formato permitido: " + url);
                    notifyFinished();
                    return;
                }

                if (!barrels.isEmpty()) {
                    try {
                        boolean alreadyVisited = barrels.getFirst().isUrlInBarrel(url);

                        if (alreadyVisited) {
                            System.out.println("[Downloader" + id + "] - URL já visitado anteriormente: " + url);
                            notifyFinished();
                            return;
                        }

                    } catch (Exception e) {
                        System.err.println("[Downloader" + id + "] - Erro ao contactar o Barrel: " + e.getMessage());
                        safeAddURL(url);
                        notifyFinished();
                        return;
                    }
                }

                System.out.println("[Downloader" + id + "] - Downloading: " + url);
                Document doc = Jsoup.connect(url).get();

                String title = doc.title();
                String text = doc.body().text();

                if (text.trim().isEmpty()) {
                    System.out.println("[Downloader" + id + "] - Página vazia ignorada: " + url);
                    notifyFinished();
                    return;
                }

                List<String> words = List.of(text.split("\\s+"));

                Elements links = doc.select("a[href]");
                List<String> outgoingLinks = links.stream()
                        .map(link -> link.absUrl("href"))
                        .filter(href -> !href.isEmpty())
                        .collect(Collectors.toList());

                PageData pageData = new PageData(url, title, words, outgoingLinks);

                sendToBarrels(pageData);

                if (outgoingLinks.size() == 1) {
                    safeAddURL(outgoingLinks.getFirst());
                    System.out.println("[Downloader" + id + "] - Adicionado 1 novo URL à Queue.");
                } else if (!outgoingLinks.isEmpty()) {
                    safeAddURLs(outgoingLinks);
                    System.out.println("[Downloader" + id + "] - Adicionados " + outgoingLinks.size() + " novos URLs à Queue.");
                }

                notifyFinished();

            } catch (Exception e) {
                System.err.println("[Downloader" + id + "] - Erro ao processar URL: " + url + ". Será re-adicionado à Queue.");
                try {
                    safeAddURL(url);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

    private void discoverBarrels() {
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
                }
            }

            barrels = discovered;

            if (barrels.isEmpty()) {
                System.out.println("[Downloader" + id + "] - Nenhum Barrel ativo encontrado. Aguardando callback...");
            } else {
                System.out.println("[Downloader" + id + "] - Barrels ativos encontrados: " + barrels.size());
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
                    barrelLock.notifyAll(); // acorda threads que estavam à espera
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
            String serverIP = args.length > 0 ? args[0] : "localhost";
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

            System.setProperty("java.rmi.server.hostname", java.net.InetAddress.getLocalHost().getHostAddress());

            Registry registry = LocateRegistry.getRegistry(serverIP, port);
            IQueue queue = (IQueue) registry.lookup("URLQueueInterface");

            Downloader downloader = new Downloader(queue, serverIP, port);
            IDownloader stub = (IDownloader) UnicastRemoteObject.exportObject(downloader, 0);

            String downloaderName = "Downloader" + downloader.id;
            //registry.rebind(downloaderName, stub);
            System.out.println("[Downloader" + downloader.id + "] - Registado no RMI Registry como '" + downloaderName + "'.");

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
