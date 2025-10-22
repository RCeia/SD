package barrel;

import common.PageData;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/**
 * Implementação do Storage Barrel.
 * Armazena o índice invertido e replica dados entre barrels.
 */
public class Barrel extends UnicastRemoteObject implements IBarrel {

    private final Map<String, Set<String>> invertedIndex = new HashMap<>();
    private final Map<String, Set<String>> incomingLinks = new HashMap<>();
    private final List<IBarrel> replicas = new ArrayList<>();
    private final String name;

    public Barrel(String name) throws RemoteException {
        super();
        this.name = name;
    }
    @Override
    public synchronized Map<String, Set<String>> getInvertedIndex() throws RemoteException {
        return new HashMap<>(invertedIndex);
    }

    @Override
    public synchronized Map<String, Set<String>> getIncomingLinksMap() throws RemoteException {
        return new HashMap<>(incomingLinks);
    }

    @Override
    public synchronized boolean isUrlInBarrels(String url) throws RemoteException {
        for (Set<String> referringUrls : incomingLinks.values()) {
            if (referringUrls.contains(url)) {
                // O URL já foi referenciado, então já foi visitado
                return true;
            }
        }
        // Se não encontrar o URL, ele não foi visitado
        return false;
    }

    // --- Armazenamento e replicação ---
    @Override
    public synchronized void storePage(PageData page) throws RemoteException {
        String url = page.getUrl();

        // Atualizar índice invertido (palavra -> URL)
        for (String word : page.getWords()) {
            invertedIndex.computeIfAbsent(word.toLowerCase(), _ -> new HashSet<>()).add(url);
        }

        // Atualizar mapa de links recebidos (link -> quem aponta para ele)
        for (String link : page.getOutgoingLinks()) {
            incomingLinks.computeIfAbsent(link, _ -> new HashSet<>()).add(url);
        }

        // Difundir para as réplicas
        for (IBarrel replica : replicas) {
            try {
                replica.replicate(page);
            } catch (Exception e) {
                System.err.println("Falha ao replicar para barrel: " + e.getMessage());
            }
        }

        System.out.println("[" + name + "] Página armazenada e replicada: " + url);
    }

    @Override
    public synchronized void replicate(PageData page) throws RemoteException {
        String url = page.getUrl();
        for (String word : page.getWords()) {
            invertedIndex.computeIfAbsent(word.toLowerCase(), _ -> new HashSet<>()).add(url);
        }
        for (String link : page.getOutgoingLinks()) {
            incomingLinks.computeIfAbsent(link, _ -> new HashSet<>()).add(url);
        }
        System.out.println("[" + name + "] Réplica recebida de: " + url);
    }

    // --- Pesquisa e estatísticas ---
    @Override
    public Map<String, String> search(List<String> terms) throws RemoteException {
        Map<String, String> results = new LinkedHashMap<>();
        for (String term : terms) {
            Set<String> urls = invertedIndex.get(term.toLowerCase());
            if (urls != null) {
                for (String url : urls)
                    results.put(url, "Contém: " + term);
            }
        }
        return results;
    }

    @Override
    public Set<String> getIncomingLinks(String url) throws RemoteException {
        return incomingLinks.getOrDefault(url, Collections.emptySet());
    }

    @Override
    public int getIndexSize() throws RemoteException {
        return invertedIndex.size();
    }

    private void discoverOtherBarrels(Registry registry) {
        try {
            String[] boundNames = registry.list();
            for (String bound : boundNames) {
                if (bound.startsWith("Barrel") && !bound.equals(name)) {
                    try {
                        IBarrel replica = (IBarrel) registry.lookup(bound);

                        // Testar se o barrel está vivo
                        try {
                            replica.getIndexSize(); // método simples de ping
                            replicas.add(replica);
                            System.out.println("[" + name + "] Conectado a réplica viva: " + bound);

                            // Sincronizar índices
                            Map<String, Set<String>> otherIndex = replica.getInvertedIndex();
                            Map<String, Set<String>> otherIncoming = replica.getIncomingLinksMap();

                            for (Map.Entry<String, Set<String>> entry : otherIndex.entrySet()) {
                                invertedIndex
                                        .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                                        .addAll(entry.getValue());
                            }

                            for (Map.Entry<String, Set<String>> entry : otherIncoming.entrySet()) {
                                incomingLinks
                                        .computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                                        .addAll(entry.getValue());
                            }

                            System.out.println("[" + name + "] Sincronizado com " + bound);

                        } catch (RemoteException e) {
                            System.err.println("[" + name + "] Barrel " + bound + " inativo, ignorado.");
                        }

                    } catch (Exception e) {
                        System.err.println("[" + name + "] Falha ao ligar a " + bound + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[" + name + "] Erro na autodescoberta: " + e.getMessage());
        }
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

    // --- Main ---
    public static void main(String[] args) {
        try {
            String name = args.length > 0 ? args[0] : "Barrel" + new Random().nextInt(1000);
            Barrel barrel = new Barrel(name);

            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            registry.rebind(name, barrel);
            System.out.println("[" + name + "] Registado no RMI Registry.");

            // Descobrir automaticamente outros barrels
            barrel.discoverOtherBarrels(registry);

            // Thread para comandos no terminal
            new Thread(() -> {
                Scanner sc = new Scanner(System.in);
                while (true) {
                    System.out.print("Comando ('show' para listar links): ");
                    String cmd = sc.nextLine().trim();
                    if (cmd.equalsIgnoreCase("show")) {
                        barrel.printStoredLinks();
                    } else if (cmd.equalsIgnoreCase("exit")) {
                        System.out.println("Encerrando " + name + "...");
                        System.exit(0);
                    } else {
                        System.out.println("Comando desconhecido. Use 'show' ou 'exit'.");
                    }
                }
            }).start();

            // Fica ativo indefinidamente
            synchronized (barrel) {
                barrel.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
