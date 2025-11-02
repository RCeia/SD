package barrel;

import common.PageData;
import downloader.IDownloader;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.InetAddress;

/**
 * Implementação de um Barrel (nó de armazenamento).
 * Cada barrel mantém um índice invertido local e pode sincronizar-se com outros barrels.
 */
public class Barrel extends UnicastRemoteObject implements IBarrel {

    private final Map<String, Set<String>> invertedIndex = new HashMap<>();
    private final Map<String, Set<String>> incomingLinks = new HashMap<>();
    private final String name;
    private boolean isActive = false;

    public Barrel(String name) throws RemoteException {
        super();
        this.name = name;
    }

    // -------------------------------------------------------------------------
    // Acesso aos índices (com cópia profunda)
    // -------------------------------------------------------------------------
    @Override
    public synchronized Map<String, Set<String>> getInvertedIndex() throws RemoteException {
        Map<String, Set<String>> copy = new HashMap<>();
        for (var entry : invertedIndex.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    @Override
    public synchronized Map<String, Set<String>> getIncomingLinksMap() throws RemoteException {
        Map<String, Set<String>> copy = new HashMap<>();
        for (var entry : incomingLinks.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
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

    // -------------------------------------------------------------------------
    // Armazenamento de páginas
    // -------------------------------------------------------------------------
    @Override
    public synchronized void storePage(PageData page) throws RemoteException {
        if (!isActive) {
            System.out.println("⏸️ [" + name + "] Em modo read-only. Ignorando storePage().");
            return;
        }

        String url = page.getUrl();

        for (String word : page.getWords()) {
            invertedIndex.computeIfAbsent(word.toLowerCase(), _ -> new HashSet<>()).add(url);
        }

        for (String link : page.getOutgoingLinks()) {
            incomingLinks.computeIfAbsent(link, _ -> new HashSet<>()).add(url);
        }

        System.out.println("📦 [" + name + "] Página armazenada: " + url);
    }

    // -------------------------------------------------------------------------
    // Descoberta e sincronização inicial
    // -------------------------------------------------------------------------
    private void discoverOtherBarrels(Registry registry) {
        try {
            String[] boundNames = registry.list();
            for (String bound : boundNames) {
                if (bound.startsWith("Barrel") && !bound.equals(name)) {
                    try {
                        IBarrel other = (IBarrel) registry.lookup(bound);
                        // Só copia de barrels ativos
                        if (!other.isActive()) continue;

                        System.out.println("🔗 [" + name + "] Encontrado barrel ativo: " + bound);
                        System.out.println("🔄 [" + name + "] Copiando índice de " + bound + "...");

                        copyIndexFrom(other);

                        isActive = true;
                        System.out.println("✅ [" + name + "] Sincronização concluída. Agora ativo!");
                        notifyDownloadersActive(registry);
                        System.out.println("🚀 [" + name + "] Barrel totalmente operacional e pronto para receber páginas!");
                        return; // já sincronizou com um barrel ativo
                    } catch (RemoteException e) {
                        System.err.println("⚠️ [" + name + "] Barrel " + bound + " inativo, ignorado.");
                    }
                }
            }

            // Se não encontrou nenhum barrel → é o primeiro
            System.out.println("🆕 [" + name + "] Primeiro barrel da rede. Marcado como ativo.");
            isActive = true;
            notifyDownloadersActive(registry);
            System.out.println("🚀 [" + name + "] Barrel totalmente operacional e pronto para receber páginas!");

        } catch (Exception e) {
            System.err.println("⚠️ [" + name + "] Erro na autodescoberta: " + e.getMessage());
        }
    }

    private synchronized void copyIndexFrom(IBarrel barrel) throws RemoteException {
        try {
            Map<String, Set<String>> otherIndex = barrel.getInvertedIndex();
            Map<String, Set<String>> otherIncoming = barrel.getIncomingLinksMap();

            for (Map.Entry<String, Set<String>> entry : otherIndex.entrySet()) {
                invertedIndex.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                    a.addAll(b);
                    return a;
                });
            }

            for (Map.Entry<String, Set<String>> entry : otherIncoming.entrySet()) {
                incomingLinks.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                    a.addAll(b);
                    return a;
                });
            }

        } catch (RemoteException e) {
            System.err.println("⚠️ [" + name + "] Falha durante cópia de índice: " + e.getMessage());
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Notificação de downloaders
    // -------------------------------------------------------------------------
    private void notifyDownloadersActive(Registry registry) {
        try {
            String[] boundNames = registry.list();
            for (String bound : boundNames) {
                if (bound.startsWith("Downloader")) {
                    try {
                        IDownloader d = (IDownloader) registry.lookup(bound);
                        d.addBarrel(this); // callback remoto
                        System.out.println("📣 [" + name + "] Notificado " + bound + " sobre novo barrel ativo.");
                    } catch (Exception e) {
                        System.err.println("⚠️ [" + name + "] Falha ao notificar " + bound + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ [" + name + "] Erro ao notificar downloaders: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Pesquisa e utilitários
    // -------------------------------------------------------------------------
    @Override
    public synchronized Map<String, String> search(List<String> terms) throws RemoteException {
        Map<String, String> results = new LinkedHashMap<>();
        for (String term : terms) {
            Set<String> urls = invertedIndex.get(term.toLowerCase());
            if (urls != null) {
                for (String url : urls) {
                    results.put(url, "Contém: " + term);
                }
            }
        }
        return results;
    }

    @Override
    public synchronized boolean isUrlInBarrel(String url) throws RemoteException {
        for (Set<String> urls : incomingLinks.values()) {
            if (urls.contains(url)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void printStoredLinks() {
        System.out.println("\n===== [" + name + "] LINKS NO ÍNDICE INVERTIDO =====");
        for (Map.Entry<String, Set<String>> entry : invertedIndex.entrySet()) {
            System.out.println("Palavra: " + entry.getKey());
            for (String url : entry.getValue()) {
                System.out.println("  - " + url);
            }
        }

        System.out.println("\n===== [" + name + "] LINKS RECEBIDOS (incomingLinks) =====");
        for (Map.Entry<String, Set<String>> entry : incomingLinks.entrySet()) {
            System.out.println("URL: " + entry.getKey() + " <- apontado por:");
            for (String origin : entry.getValue()) {
                System.out.println("  - " + origin);
            }
        }

        System.out.println("=============================================\n");
    }

    @Override
    public synchronized String getSystemStats() throws RemoteException {
        return "=== Estatísticas de " + name + " ===\n" +
                "Tamanho do índice invertido: " + invertedIndex.size() + "\n" +
                "Número de entradas em incomingLinks: " + incomingLinks.size() + "\n" +
                "Total combinado: " + (invertedIndex.size() + incomingLinks.size()) + "\n" +
                "Estado: " + (isActive ? "Ativo" : "Inativo") + "\n";
    }

    @Override
    public String toString() {
        return "[" + name + "]";
    }

    @Override
    public String getName() throws RemoteException {
        return name;
    }


    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        try {
            String name = args.length > 0 ? args[0]
                    : "Barrel" + (ProcessHandle.current().pid() * 10 + new Random().nextInt(1000));


            String registryHost = args.length > 1 ? args[1] : "localhost";
            int registryPort = args.length > 2 ? Integer.parseInt(args[2]) : 1099;

            String localIP = InetAddress.getLocalHost().getHostAddress();
            System.setProperty("java.rmi.server.hostname", localIP);
            System.out.println("[INFO] RMI hostname definido como: " + localIP);

            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);

            Barrel barrel = new Barrel(name);
            registry.rebind(name, barrel);
            System.out.println("✅ [" + name + "] Registado no RMI Registry.");

            // Descobrir automaticamente outros barrels
            barrel.discoverOtherBarrels(registry);

            // Thread para comandos no terminal
            new Thread(() -> {
                Scanner sc = new Scanner(System.in);
                while (true) {
                    System.out.print("Comando ('show' para listar links, 'exit' para sair): ");
                    String cmd = sc.nextLine().trim();

                    if (cmd.equalsIgnoreCase("show")) {
                        barrel.printStoredLinks(); // continua a mostrar os links armazenados

                        try {
                            int invertedSize = barrel.getInvertedIndex().size();
                            int incomingSize = barrel.getIncomingLinksMap().size();
                            System.out.println("\n📊 [Resumo do índice]");
                            System.out.println(" - Entradas no invertedIndex : " + invertedSize);
                            System.out.println(" - Entradas em incomingLinks : " + incomingSize);
                            System.out.println(" - Total combinado            : " + (invertedSize + incomingSize));
                        } catch (Exception e) {
                            System.err.println("⚠️ Erro ao obter estatísticas do índice: " + e.getMessage());
                        }

                    } else if (cmd.equalsIgnoreCase("exit")) {
                        try {
                            registry.unbind(name);
                            UnicastRemoteObject.unexportObject(barrel, true);
                            System.out.println("🛑 [" + name + "] Barrel removido do registry e encerrado.");
                        } catch (Exception ex) {
                            System.err.println("⚠️ [" + name + "] Erro ao encerrar: " + ex.getMessage());
                        }
                        System.exit(0);

                    } else {
                        System.out.println("❓ Comando desconhecido. Use 'show' ou 'exit'.");
                    }
                }
            }).start();


            // Mantém o processo vivo
            synchronized (barrel) {
                barrel.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}