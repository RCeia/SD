package com.googol.web.barrel;

import barrel.IBarrel;
import com.googol.web.common.PageData;
import com.googol.web.common.UrlMetadata;
import com.googol.web.downloader.IDownloader;
import com.googol.web.gateway.IGateway;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.InetAddress;

/**
 * Implementação de um Barrel (nó de armazenamento).
 * Mantém índices locais e suporta sincronização com outros barrels.
 */
public class Barrel extends UnicastRemoteObject implements IBarrel {

    // Estruturas de Dados
    private final Map<String, Set<String>> invertedIndex = new HashMap<>();
    private final Map<String, Set<String>> incomingLinks = new HashMap<>();
    private final Map<String, UrlMetadata> pageMetadata = new HashMap<>(); // Armazena Título e Citação

    // Estado do Barrel
    private final String name;
    private boolean isActive = false;

    public Barrel(String name) throws RemoteException {
        super();
        this.name = name;
    }

    // =========================================================================
    // IMPLEMENTAÇÃO DA INTERFACE IBarrel (Métodos Remotos)
    // =========================================================================

    @Override
    public synchronized void storePage(PageData page) throws RemoteException {
        if (!isActive) {
            System.out.println("[" + name + "] Em modo read-only. Ignorando storePage().");
            return;
        }

        saveMetadata(page);
        updateInvertedIndex(page);
        updateIncomingLinks(page);

        System.out.println("[" + name + "] Página armazenada: " + page.getUrl());
    }

    @Override
    public synchronized Map<String, UrlMetadata> search(List<String> terms) throws RemoteException {
        Map<String, UrlMetadata> results = new LinkedHashMap<>();

        for (String term : terms) {
            Set<String> urls = invertedIndex.get(term.toLowerCase());

            if (urls != null) {
                for (String url : urls) {
                    // Obter o objeto diretamente do mapa
                    UrlMetadata meta = pageMetadata.get(url);

                    // Proteção contra nulos (caso raro onde temos URL mas perdemos metadados)
                    if (meta == null) {
                        meta = new UrlMetadata("Sem Título", "Sem descrição disponível.");
                    }

                    // Guardar diretamente o objeto no resultado
                    results.put(url, meta);
                }
            }
        }
        return results;
    }

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
        return new HashMap<>(pageMetadata); // Cópia superficial é suficiente para objetos imutáveis/simples
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

    @Override
    public synchronized boolean isUrlInBarrel(String url) throws RemoteException {
        // Verifica se o URL existe como destino em qualquer conjunto de links
        return incomingLinks.values().stream().anyMatch(set -> set.contains(url));
    }

    @Override
    public synchronized String getSystemStats() throws RemoteException {
        return String.format("=== Estatísticas de %s ===\n" +
                        "Índice Invertido: %d\n" +
                        "Incoming Links: %d\n" +
                        "Total Entradas: %d\n" +
                        "Estado: %s\n",
                name, invertedIndex.size(), incomingLinks.size(),
                (invertedIndex.size() + incomingLinks.size()),
                (isActive ? "Ativo" : "Inativo"));
    }

    // =========================================================================
    // MÉTODOS AUXILIARES PRIVADOS (Lógica Interna)
    // =========================================================================

    private void saveMetadata(PageData page) {
        String title = page.getTitle();
        List<String> words = page.getWords();
        String citation = generateCitation(words);

        pageMetadata.put(page.getUrl(), new UrlMetadata(title, citation));
    }

    private String generateCitation(List<String> words) {
        if (words == null || words.isEmpty()) {
            return "Sem descrição disponível.";
        }
        int limit = Math.min(words.size(), 20);
        String citation = String.join(" ", words.subList(0, limit));

        if (words.size() > 20) {
            citation += "...";
        }
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

    private String formatResultValue(UrlMetadata meta) {
        if (meta != null) {
            // Formato: Título \n Citação
            return meta.getTitle() + "\n" + meta.getCitation();
        }
        return "Sem Título\nSem citação disponível.";
    }

    private Map<String, Set<String>> deepCopyMap(Map<String, Set<String>> original) {
        Map<String, Set<String>> copy = new HashMap<>();
        for (var entry : original.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    // =========================================================================
    // SINCRONIZAÇÃO E DESCOBERTA
    // =========================================================================

    private void discoverOtherBarrels(Registry registry) {
        try {
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
        System.out.println("[" + name + "] " + (isFirst ? "Primeiro barrel da rede." : "Sincronização concluída.") + " Ativando...");
        this.isActive = true;
        notifyDownloadersActive(registry);
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
            System.err.println("[" + name + "] Erro crítico na cópia de índice.");
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
                        System.out.println("[" + name + "] Notificado: " + bound);
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception e) {
            System.err.println("[" + name + "] Erro ao notificar downloaders.");
        }
    }

    public synchronized void printStoredLinks() {
        System.out.println("\n===== [" + name + "] ESTADO ATUAL =====");
        System.out.println("Palavras Indexadas: " + invertedIndex.size());
        System.out.println("Páginas Conhecidas: " + incomingLinks.size());
        System.out.println("Metadados Armazenados: " + pageMetadata.size());
        System.out.println("====================================\n");
    }

    @Override
    public String toString() {
        return "[" + name + "]";
    }

    // =========================================================================
    // MAIN
    // =========================================================================
    public static void main(String[] args) {
        try {
            String name = "Barrel" + (ProcessHandle.current().pid() * 10 + new Random().nextInt(1000));

            // Configuração RMI
            String registryHost = args.length > 0 ? args[0] : "localhost";
            int registryPort = args.length > 1 ? Integer.parseInt(args[1]) : 1099;
            String localIP = InetAddress.getLocalHost().getHostAddress();
            System.setProperty("java.rmi.server.hostname", localIP);

            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
            Barrel barrel = new Barrel(name);
            registry.rebind(name, barrel);
            System.out.println("[" + name + "] Barrel iniciado em " + localIP);

            // Processo de Arranque
            barrel.discoverOtherBarrels(registry);
            startGatewayRegistration(registry, barrel, name);
            startConsoleHandler(registry, barrel, name);

            // Manter vivo
            synchronized (barrel) {
                barrel.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startGatewayRegistration(Registry registry, Barrel barrel, String name) {
        new Thread(() -> {
            while (true) {
                try {
                    IGateway gateway = (IGateway) registry.lookup("Gateway");
                    gateway.registerBarrel(barrel);
                    System.out.println("[" + name + "] Registado na Gateway.");
                    break;
                } catch (Exception e) {
                    System.out.println("[" + name + "] À espera da Gateway...");
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private static void startConsoleHandler(Registry registry, Barrel barrel, String name) {
        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            while (true) {
                System.out.print("> ");
                String cmd = sc.nextLine().trim();

                if (cmd.equalsIgnoreCase("show")) {
                    barrel.printStoredLinks();
                } else if (cmd.equalsIgnoreCase("exit")) {
                    try {
                        registry.unbind(name);
                        UnicastRemoteObject.unexportObject(barrel, true);
                        System.out.println("Barrel encerrado.");
                    } catch (Exception ignored) {}
                    System.exit(0);
                }
            }
        }).start();
    }
}