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

public class Barrel extends UnicastRemoteObject implements IBarrel {

    // Estruturas de Dados
    private final Map<String, Set<String>> invertedIndex = new HashMap<>();
    private final Map<String, Set<String>> incomingLinks = new HashMap<>();
    private final Map<String, UrlMetadata> pageMetadata = new HashMap<>();
    private IGateway gateway;

    // Estado do Barrel
    private final String name;
    private boolean isActive = false;

    public Barrel(String name) throws RemoteException {
        super();
        this.name = name;
    }

    // =========================================================================
    // IMPLEMENTAÇÃO DA INTERFACE IBarrel
    // =========================================================================

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

    @Override
    public synchronized Map<String, UrlMetadata> search(List<String> terms) throws RemoteException {
        Map<String, UrlMetadata> results = new LinkedHashMap<>();
        if (!isActive) return results; // Se não estiver ativo, retorna vazio

        for (String term : terms) {
            Set<String> urls = invertedIndex.get(term.toLowerCase());
            if (urls != null) {
                for (String url : urls) {
                    UrlMetadata meta = pageMetadata.get(url);
                    if (meta == null) meta = new UrlMetadata("Sem Título", "Sem descrição.");
                    results.put(url, meta);
                }
            }
        }
        return results;
    }

    // Getters padrão da interface...
    @Override
    public synchronized Map<String, Set<String>> getInvertedIndex() throws RemoteException {
        return deepCopyMap(invertedIndex);
    }

    @Override
    public synchronized Map<String, Set<String>> getIncomingLinksMap() throws RemoteException {
        return deepCopyMap(incomingLinks);
    }

    @Override
    public synchronized Map<String, UrlMetadata> getPageMetadata() throws RemoteException {
        return new HashMap<>(pageMetadata);
    }

    @Override
    public synchronized Set<String> getIncomingLinks(String url) throws RemoteException {
        return incomingLinks.getOrDefault(url, Collections.emptySet());
    }

    @Override
    public int getIndexSize() throws RemoteException {
        return invertedIndex.size();
    }

    @Override
    public boolean isActive() throws RemoteException {
        return isActive;
    }

    @Override
    public String getName() throws RemoteException {
        return name;
    }

    public void setGateway(IGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public synchronized boolean isUrlInBarrel(String url) throws RemoteException {
        return incomingLinks.values().stream().anyMatch(set -> set.contains(url));
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    private void saveMetadata(PageData page) {
        String title = page.getTitle();
        List<String> words = page.getWords();
        String citation = generateCitation(words);
        pageMetadata.put(page.getUrl(), new UrlMetadata(title, citation));
    }

    private String generateCitation(List<String> words) {
        if (words == null || words.isEmpty()) return "Sem descrição.";
        int limit = Math.min(words.size(), 20);
        String citation = String.join(" ", words.subList(0, limit));
        if (words.size() > 20) citation += "...";
        return citation;
    }

    private void updateInvertedIndex(PageData page) {
        if (page.getWords() == null) return;
        for (String word : page.getWords()) {
            invertedIndex.computeIfAbsent(word.toLowerCase(), k -> new HashSet<>()).add(page.getUrl());
        }
    }

    private void updateIncomingLinks(PageData page) {
        if (page.getOutgoingLinks() == null) return;
        for (String link : page.getOutgoingLinks()) {
            incomingLinks.computeIfAbsent(link, k -> new HashSet<>()).add(page.getUrl());
        }
    }

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
     * NOVA VERSÃO: Recebe o status.
     * Se status == "SYNCHING", envia 0 de carga.
     * Se status == "ACTIVE", envia carga real.
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

    private void activateBarrel(Registry registry, boolean isFirst) {
        System.out.println("[" + name + "] " + (isFirst ? "Primeiro da rede." : "Sync concluído.") + " A ativar...");
        this.isActive = true;

        notifyDownloadersActive(registry);

        // 2. FINAL DA SINCRONIZAÇÃO: Avisar Gateway que estou pronto (Carga Real)
        sendStatsToGateway("ACTIVE");

        System.out.println("[" + name + "] Barrel operacional.");
    }

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

    private void mergeMap(Map<String, Set<String>> target, Map<String, Set<String>> source) {
        for (var entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                a.addAll(b);
                return a;
            });
        }
    }

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

    // Função de espera bloqueante simples
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