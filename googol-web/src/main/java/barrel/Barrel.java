package barrel;

import common.PageData;
import common.UrlMetadata;
import downloader.IDownloader;
import gateway.IGateway;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.InetAddress;

/**
 * Implementação do nó de armazenamento (Barrel) do motor de busca.
 * <p>
 * Esta classe gere três estruturas de dados principais:
 * <ul>
 * <li><b>Índice Invertido:</b> Mapeia palavras para URLs.</li>
 * <li><b>Links de Entrada:</b> Mapeia URLs para quem aponta para eles (para ranking).</li>
 * <li><b>Metadados:</b> Guarda títulos e citações para exibição rápida.</li>
 * </ul>
 * <p>
 * O Barrel possui também lógica de sincronização automática ao iniciar (copia dados de pares existentes)
 * e reporta o seu estado e carga ao Gateway.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 2.0
 */
public class Barrel extends UnicastRemoteObject implements IBarrel {

    // Estruturas de Dados
    /**
     * Estrutura principal de pesquisa: Termo -> Conjunto de URLs que o contêm.
     */
    private final Map<String, Set<String>> invertedIndex = new HashMap<>();

    /**
     * Grafo de ligações: URL Destino -> Conjunto de URLs Origem. Usado para ranking.
     */
    private final Map<String, Set<String>> incomingLinks = new HashMap<>();

    /**
     * Armazenamento de informação de exibição: URL -> (Título, Citação).
     */
    private final Map<String, UrlMetadata> pageMetadata = new HashMap<>();

    /**
     * Referência para o Gateway central.
     */
    private IGateway gateway;

    // Estado do Barrel
    /**
     * Nome único deste Barrel.
     */
    private final String name;

    /**
     * Flag que indica se o Barrel completou a sincronização e está pronto para servir pedidos.
     */
    private boolean isActive = false;

    /**
     * Construtor do Barrel.
     *
     * @param name O nome identificador do Barrel.
     * @throws RemoteException Se ocorrer erro na exportação RMI.
     */
    public Barrel(String name) throws RemoteException {
        super();
        this.name = name;
    }

    // =========================================================================
    // IMPLEMENTAÇÃO DA INTERFACE IBarrel
    // =========================================================================

    /**
     * Armazena uma página recebida de um Downloader.
     * <p>
     * Se o Barrel não estiver ativo (ainda em sincronização), o pedido é ignorado.
     * Caso contrário, atualiza todas as estruturas de dados e notifica o Gateway com novas estatísticas.
     * </p>
     *
     * @param page Dados da página a armazenar.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public synchronized void storePage(PageData page) throws RemoteException {
        if (!isActive) {
            System.out.println("[" + name + "] Em modo Synching/ReadOnly. Ignorando storePage().");
            return;
        }

        saveMetadata(page);
        updateInvertedIndex(page);
        updateIncomingLinks(page);

        System.out.println("[" + name + "] Página armazenada: " + page.getUrl());

        // Atualiza estatísticas reais pois está ativo
        sendStatsToGateway("ACTIVE");
    }

    /**
     * Executa a lógica de pesquisa completa.
     * <p>
     * 1. Faz parse dos termos e deteta tags de paginação [PAGE:X].<br>
     * 2. Recupera URLs do índice invertido.<br>
     * 3. Ordena os resultados com base no número de incoming links (relevância).<br>
     * 4. Aplica a paginação (ex: retorna apenas resultados 11-20).<br>
     * 5. Inclui um metadado especial "##META_STATS##" com o total real de resultados.
     * </p>
     *
     * @param terms Lista de termos de pesquisa.
     * @return Mapa ordenado contendo os resultados da página solicitada.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public synchronized Map<String, UrlMetadata> search(List<String> terms) throws RemoteException {
        if (!isActive) return new HashMap<>();

        // 1. Lógica de Paginação (Parse do [PAGE:X])
        int page = 1;
        int pageSize = 10;
        List<String> realTerms = new ArrayList<>();

        for (String t : terms) {
            if (t.contains("[PAGE:")) {
                try {
                    int idx = t.lastIndexOf("[PAGE:");
                    String num = t.substring(idx + 6, t.indexOf("]", idx));
                    page = Integer.parseInt(num);
                    // Adiciona apenas a palavra real à lista de pesquisa
                    realTerms.add(t.substring(0, idx));
                } catch (Exception e) {
                    realTerms.add(t); // Fallback se falhar o parse
                }
            } else {
                realTerms.add(t);
            }
        }

        // 2. Coletar TODOS os resultados (Sem duplicados)
        Set<String> uniqueUrls = new HashSet<>();
        for (String term : realTerms) {
            Set<String> urls = invertedIndex.get(term.toLowerCase());
            if (urls != null) {
                uniqueUrls.addAll(urls);
            }
        }

        // 3. Converter para Lista para poder ORDENAR
        List<String> sortedUrls = new ArrayList<>(uniqueUrls);

        // AQUI ESTÁ A ORDENAÇÃO: Quem tem mais incomingLinks fica em primeiro
        sortedUrls.sort((url1, url2) -> {
            int count1 = incomingLinks.getOrDefault(url1, Collections.emptySet()).size();
            int count2 = incomingLinks.getOrDefault(url2, Collections.emptySet()).size();
            return Integer.compare(count2, count1); // Ordem Decrescente
        });

        // 4. Calcular Paginação e TOTAL REAL
        int totalReal = sortedUrls.size(); // <--- Guardamos o total aqui!
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, totalReal);

        Map<String, UrlMetadata> pageResults = new LinkedHashMap<>();

        // 5. Construir o Mapa apenas com os 10 itens vencedores
        // Só entramos no loop se a página pedida for válida
        if (start < totalReal && start >= 0) {
            for (int i = start; i < end; i++) {
                String url = sortedUrls.get(i);
                UrlMetadata meta = pageMetadata.get(url);
                if (meta == null) meta = new UrlMetadata("Sem Título", "Sem descrição.");
                pageResults.put(url, meta);
            }
        }

        // 6. O TRUQUE FINAL: Enviar o total real numa entrada especial
        // O Controller vai ler isto, guardar o número e remover a entrada.
        pageResults.put("##META_STATS##", new UrlMetadata("Stats", String.valueOf(totalReal)));

        System.out.println("[" + name + "] Pesquisa por " + realTerms + " (Pag " + page + ") enviando " + (pageResults.size() - 1) + " de " + totalReal + " resultados.");

        return pageResults;
    }

    // Getters padrão da interface...

    /**
     * Retorna uma cópia profunda (Deep Copy) do índice invertido.
     * @return Mapa duplicado do índice.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public synchronized Map<String, Set<String>> getInvertedIndex() throws RemoteException {
        return deepCopyMap(invertedIndex);
    }

    /**
     * Retorna uma cópia profunda (Deep Copy) do mapa de incoming links.
     * @return Mapa duplicado dos links.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public synchronized Map<String, Set<String>> getIncomingLinksMap() throws RemoteException {
        return deepCopyMap(incomingLinks);
    }

    /**
     * Retorna uma cópia dos metadados.
     * @return Mapa de metadados.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public synchronized Map<String, UrlMetadata> getPageMetadata() throws RemoteException {
        return new HashMap<>(pageMetadata);
    }

    /**
     * Retorna os links que apontam para um URL específico.
     * @param url URL alvo.
     * @return Conjunto de URLs de origem.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public synchronized Set<String> getIncomingLinks(String url) throws RemoteException {
        return incomingLinks.getOrDefault(url, Collections.emptySet());
    }

    /**
     * Obtém o número total de termos indexados.
     * @return Tamanho do índice.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public int getIndexSize() throws RemoteException {
        return invertedIndex.size();
    }

    /**
     * Verifica se o Barrel está ativo.
     * @return true se ativo, false se em sincronização.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public boolean isActive() throws RemoteException {
        return isActive;
    }

    /**
     * Obtém o nome do Barrel.
     * @return Nome.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public String getName() throws RemoteException {
        return name;
    }

    /**
     * Define a referência para o Gateway.
     * @param gateway Objeto remoto Gateway.
     */
    public void setGateway(IGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Verifica se um URL é conhecido (está presente em algum valor do mapa de incoming links).
     * @param url URL a verificar.
     * @return true se encontrado.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public synchronized boolean isUrlInBarrel(String url) throws RemoteException {
        return incomingLinks.values().stream().anyMatch(set -> set.contains(url));
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    /**
     * Extrai e salva os metadados (Título e Citação) de uma página.
     * A citação é gerada a partir das primeiras 20 palavras.
     *
     * @param page Dados da página.
     */
    private void saveMetadata(PageData page) {
        String title = page.getTitle();
        List<String> words = page.getWords();
        String citation = generateCitation(words);
        pageMetadata.put(page.getUrl(), new UrlMetadata(title, citation));
    }

    /**
     * Gera uma string de citação com as primeiras palavras do texto.
     *
     * @param words Lista de palavras.
     * @return String truncada.
     */
    private String generateCitation(List<String> words) {
        if (words == null || words.isEmpty()) return "Sem descrição.";
        int limit = Math.min(words.size(), 20);
        String citation = String.join(" ", words.subList(0, limit));
        if (words.size() > 20) citation += "...";
        return citation;
    }

    /**
     * Atualiza o índice invertido mapeando cada palavra da página ao seu URL.
     *
     * @param page Dados da página.
     */
    private void updateInvertedIndex(PageData page) {
        if (page.getWords() == null) return;
        for (String word : page.getWords()) {
            invertedIndex.computeIfAbsent(word.toLowerCase(), k -> new HashSet<>()).add(page.getUrl());
        }
    }

    /**
     * Atualiza o mapa de Incoming Links com base nos links de saída da página.
     * Se a página P aponta para L, então L recebe P na sua lista de entrada.
     *
     * @param page Dados da página.
     */
    private void updateIncomingLinks(PageData page) {
        if (page.getOutgoingLinks() == null) return;
        for (String link : page.getOutgoingLinks()) {
            incomingLinks.computeIfAbsent(link, k -> new HashSet<>()).add(page.getUrl());
        }
    }

    /**
     * Cria uma cópia profunda de um mapa do tipo Map<String, Set<String>>.
     * Necessário para garantir thread-safety e evitar ConcurrentModificationException durante a sincronização.
     *
     * @param original O mapa original.
     * @return Uma cópia independente.
     */
    private Map<String, Set<String>> deepCopyMap(Map<String, Set<String>> original) {
        Map<String, Set<String>> copy = new HashMap<>();
        for (var entry : original.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    // =========================================================================
    // SINCRONIZAÇÃO E LÓGICA DE ESTADO (ALTERADO AQUI)
    // =========================================================================

    /**
     * Envia as estatísticas atuais de carga para o Gateway.
     * <p>
     * Se o estado for "SYNCHING", reporta tamanho 0 para evitar que o Gateway
     * encaminhe pesquisas para este nó enquanto ele ainda está a carregar dados.
     * </p>
     *
     * @param status String indicando o estado ("ACTIVE" ou "SYNCHING").
     */
    private void sendStatsToGateway(String status) {
        try {
            if (gateway != null) {
                int invSize = 0;
                int incSize = 0;

                // Apenas calculamos o tamanho real se estivermos no estado ACTIVE
                if ("ACTIVE".equalsIgnoreCase(status)) {
                    invSize = invertedIndex.size();
                    incSize = incomingLinks.size();
                }

                // Envia para a gateway.
                // Nota: Mesmo que a Gateway não receba a string 'status',
                // ao receber (0,0) ela sabe que este barrel não deve receber carga.
                gateway.updateBarrelIndexSize(this, invSize, incSize);

                System.out.println("[" + name + "] Stats enviadas. Estado: " + status + " (Load: " + invSize + ")");
            }
        } catch (RemoteException e) {
            System.err.println("[" + name + "] Erro ao enviar estatísticas: " + e.getMessage());
        }
    }

    /**
     * Tenta descobrir outros Barrels na rede para sincronizar dados.
     * <p>
     * Se encontrar outro Barrel ativo, copia os seus dados.
     * Se não encontrar ninguém ou todos falharem, assume-se como o primeiro da rede.
     * </p>
     *
     * @param registry O RMI Registry para lookup.
     */
    private void discoverOtherBarrels(Registry registry) {
        try {
            // 1. ANTES DE TUDO: Avisar Gateway que existo mas estou a sincronizar (Zero Load)
            sendStatsToGateway("SYNCHING");

            String[] boundNames = registry.list();
            for (String bound : boundNames) {
                if (bound.startsWith("Barrel") && !bound.equals(name)) {
                    if (trySyncWith(registry, bound)) return;
                }
            }
            // Se chegou aqui, é o primeiro
            activateBarrel(registry, true);

        } catch (Exception e) {
            System.err.println("[" + name + "] Erro na autodescoberta: " + e.getMessage());
        }
    }

    /**
     * Tenta realizar a sincronização (cópia de dados) a partir de um Barrel específico.
     *
     * @param registry O RMI Registry.
     * @param barrelName O nome do Barrel alvo.
     * @return true se a sincronização for bem sucedida.
     */
    private boolean trySyncWith(Registry registry, String barrelName) {
        try {
            IBarrel other = (IBarrel) registry.lookup(barrelName);
            if (!other.isActive()) return false;

            System.out.println("[" + name + "] A sincronizar com: " + barrelName + "...");
            copyIndexFrom(other);
            activateBarrel(registry, false);
            return true;
        } catch (Exception e) {
            System.err.println("[" + name + "] Falha ao sincronizar com " + barrelName);
            return false;
        }
    }

    /**
     * Marca o Barrel como ativo e notifica componentes externos (Downloaders e Gateway).
     *
     * @param registry O RMI Registry.
     * @param isFirst Indica se este é o primeiro Barrel da rede.
     */
    private void activateBarrel(Registry registry, boolean isFirst) {
        System.out.println("[" + name + "] " + (isFirst ? "Primeiro da rede." : "Sync concluído.") + " A ativar...");
        this.isActive = true;

        notifyDownloadersActive(registry);

        // 2. FINAL DA SINCRONIZAÇÃO: Avisar Gateway que estou pronto (Carga Real)
        sendStatsToGateway("ACTIVE");

        System.out.println("[" + name + "] Barrel operacional.");
    }

    /**
     * Copia todos os dados (índice, links, metadados) de outro Barrel.
     *
     * @param barrel A referência remota do Barrel fonte.
     * @throws RemoteException Se ocorrer erro na transferência.
     */
    private synchronized void copyIndexFrom(IBarrel barrel) throws RemoteException {
        try {
            Map<String, Set<String>> otherIndex = barrel.getInvertedIndex();
            Map<String, Set<String>> otherIncoming = barrel.getIncomingLinksMap();
            Map<String, UrlMetadata> otherMetadata = barrel.getPageMetadata();

            mergeMap(invertedIndex, otherIndex);
            mergeMap(incomingLinks, otherIncoming);
            pageMetadata.putAll(otherMetadata);
        } catch (RemoteException e) {
            throw e;
        }
    }

    /**
     * Método auxiliar para fundir dois mapas de conjuntos.
     *
     * @param target Mapa de destino.
     * @param source Mapa de origem.
     */
    private void mergeMap(Map<String, Set<String>> target, Map<String, Set<String>> source) {
        for (var entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                a.addAll(b);
                return a;
            });
        }
    }

    /**
     * Procura por Downloaders na rede e regista-se neles para começar a receber URLs.
     *
     * @param registry O RMI Registry.
     */
    private void notifyDownloadersActive(Registry registry) {
        try {
            for (String bound : registry.list()) {
                if (bound.startsWith("Downloader")) {
                    try {
                        IDownloader d = (IDownloader) registry.lookup(bound);
                        d.addBarrel(this);
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Imprime no terminal o estado atual do Barrel.
     */
    public synchronized void printStoredLinks() {
        System.out.println("\n===== [" + name + "] ESTADO =====");
        System.out.println("Status: " + (isActive ? "ACTIVE" : "SYNCHING"));
        System.out.println("Palavras: " + invertedIndex.size());
        System.out.println("Links: " + incomingLinks.size());
        System.out.println("==============================\n");
    }

    @Override
    public String toString() { return "[" + name + "]"; }

    // =========================================================================
    // MAIN
    // =========================================================================

    /**
     * Método principal de arranque do servidor Barrel.
     * <p>
     * 1. Regista-se no RMI.<br>
     * 2. Espera pela conexão com o Gateway.<br>
     * 3. Inicia o processo de sincronização/descoberta.<br>
     * 4. Inicia uma thread para comandos de consola.
     * </p>
     *
     * @param args Argumentos de linha de comando: [0] Host do Registry, [1] Porta do Registry.
     */
    public static void main(String[] args) {
        try {
            String name = "Barrel" + (ProcessHandle.current().pid());
            String registryHost = args.length > 0 ? args[0] : "localhost";
            int registryPort = args.length > 1 ? Integer.parseInt(args[1]) : 1099;
            String localIP = InetAddress.getLocalHost().getHostAddress();
            System.setProperty("java.rmi.server.hostname", localIP);

            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
            Barrel barrel = new Barrel(name);
            registry.rebind(name, barrel);
            System.out.println("[" + name + "] Iniciado em " + localIP);

            // 1. ESPERA PELA GATEWAY (Bloqueante)
            // Precisamos disto aqui para poder enviar o "SYNCHING" antes de começar a procurar outros barrels
            waitForGateway(registry, barrel, name);

            // 2. INICIA PROCESSO DE SINCRONIZAÇÃO
            // Aqui dentro chamará sendStatsToGateway("SYNCHING") no início
            // e sendStatsToGateway("ACTIVE") no fim.
            barrel.discoverOtherBarrels(registry);

            startConsoleHandler(registry, barrel, name);

            synchronized (barrel) { barrel.wait(); }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Loop infinito até encontrar o Gateway no registo RMI.
     *
     * @param registry Referência para o Registry.
     * @param barrel A instância local do Barrel.
     * @param name Nome do Barrel para logs.
     */
    private static void waitForGateway(Registry registry, Barrel barrel, String name) {
        System.out.println("[" + name + "] A procurar Gateway...");
        while (true) {
            try {
                IGateway gateway = (IGateway) registry.lookup("Gateway");
                barrel.setGateway(gateway);
                gateway.registerBarrel(barrel);
                System.out.println("[" + name + "] Gateway conectada.");
                break;
            } catch (Exception e) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Inicia uma thread para processar comandos de consola (ex: "show", "exit").
     *
     * @param registry Referência para o Registry.
     * @param barrel A instância local do Barrel.
     * @param name Nome do Barrel.
     */
    private static void startConsoleHandler(Registry registry, Barrel barrel, String name) {
        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            while (true) {
                try { Thread.sleep(100); } catch(Exception e){}
                System.out.print("> ");
                if (sc.hasNextLine()) {
                    String cmd = sc.nextLine().trim();
                    if (cmd.equalsIgnoreCase("show")) barrel.printStoredLinks();
                    else if (cmd.equalsIgnoreCase("exit")) System.exit(0);
                }
            }
        }).start();
    }
}