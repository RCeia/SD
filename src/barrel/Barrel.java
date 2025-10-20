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

    // --- Armazenamento e replicação ---
    @Override
    public synchronized void storePage(PageData page) throws RemoteException {
        String url = page.getUrl();

        // Atualizar índice invertido (palavra -> URL)
        for (String word : page.getWords()) {
            invertedIndex.computeIfAbsent(word.toLowerCase(), k -> new HashSet<>()).add(url);
        }

        // Atualizar mapa de links recebidos (link -> quem aponta para ele)
        for (String link : page.getOutgoingLinks()) {
            incomingLinks.computeIfAbsent(link, k -> new HashSet<>()).add(url);
        }

        // Difundir para as réplicas
        for (IBarrel replica : replicas) {
            try {
                replica.replicate(page);
            } catch (Exception e) {
                System.err.println("⚠️ Falha ao replicar para barrel: " + e.getMessage());
            }
        }

        System.out.println("📦 [" + name + "] Página armazenada e replicada: " + url);
    }

    @Override
    public synchronized void replicate(PageData page) throws RemoteException {
        String url = page.getUrl();
        for (String word : page.getWords()) {
            invertedIndex.computeIfAbsent(word.toLowerCase(), k -> new HashSet<>()).add(url);
        }
        for (String link : page.getOutgoingLinks()) {
            incomingLinks.computeIfAbsent(link, k -> new HashSet<>()).add(url);
        }
        System.out.println("🔁 [" + name + "] Réplica recebida de: " + url);
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

    // --- Autodescoberta de outros barrels registados ---
    private void discoverOtherBarrels(Registry registry) {
        try {
            String[] boundNames = registry.list();
            for (String bound : boundNames) {
                if (bound.startsWith("Barrel") && !bound.equals(name)) {
                    try {
                        IBarrel replica = (IBarrel) registry.lookup(bound);
                        replicas.add(replica);
                        System.out.println("🔗 [" + name + "] Ligado automaticamente a réplica: " + bound);
                    } catch (Exception e) {
                        System.err.println("⚠️ [" + name + "] Falha ao ligar a " + bound + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ [" + name + "] Erro na autodescoberta: " + e.getMessage());
        }
    }

    // --- Main ---
    public static void main(String[] args) {
        try {
            String name = args.length > 0 ? args[0] : "Barrel" + new Random().nextInt(1000);
            Barrel barrel = new Barrel(name);

            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            registry.rebind(name, barrel);
            System.out.println("✅ [" + name + "] Registado no RMI Registry.");

            // Descobrir automaticamente outros barrels
            barrel.discoverOtherBarrels(registry);

            // Fica ativo indefinidamente
            synchronized (barrel) {
                barrel.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
