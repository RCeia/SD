package gateway;

import queue.IQueue;
import barrel.IBarrel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class Gateway extends UnicastRemoteObject implements IGateway {

    private final IQueue queue;
    private final Map<IBarrel, Long> barrels; // Barrel -> last access time
    private final Random random;

    public Gateway() throws RemoteException {
        super();
        this.barrels = new HashMap<>();
        this.random = new Random();

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);

            // Connect to the URLQueue
            queue = (IQueue) registry.lookup("URLQueueInterface");
            System.out.println("[Gateway] URLQueue conectada com sucesso.");

            // Discover barrels registered as Barrel*
            for (String name : registry.list()) {
                if (name.startsWith("Barrel")) {
                    IBarrel barrel = (IBarrel) registry.lookup(name);
                    barrels.put(barrel, 0L); // 0 = never used
                    System.out.println("[Gateway] Barrel encontrado e registado: " + name);
                }
            }

        } catch (Exception e) {
            System.err.println("[Gateway] Erro ao conectar ao RMI Registry: " + e.getMessage());
            throw new RemoteException("Erro ao conectar ao RMI Registry", e);
        }
    }

    private IBarrel chooseBarrel() {
        if (barrels.isEmpty()) {
            System.err.println("[Gateway] Nenhum Barrel disponível!");
            return null;
        }

        // Never used barrels
        List<IBarrel> neverUsed = new ArrayList<>();
        long oldestTime = Long.MAX_VALUE;
        IBarrel oldestBarrel = null;

        for (Map.Entry<IBarrel, Long> entry : barrels.entrySet()) {
            long lastUsed = entry.getValue();
            if (lastUsed == 0) {
                neverUsed.add(entry.getKey());
            } else if (lastUsed < oldestTime) {
                oldestTime = lastUsed;
                oldestBarrel = entry.getKey();
            }
        }

        if (!neverUsed.isEmpty()) {
            return neverUsed.get(random.nextInt(neverUsed.size()));
        }
        return oldestBarrel;
    }

    @Override
    public String indexURL(String url) throws RemoteException {
        System.out.println("[Gateway] Método indexURL chamado com o URL: " + url);

        if (queue != null) {
            queue.addURL(url);
            return "URL '" + url + "' indexado com sucesso na fila!";
        } else {
            return "Erro: Queue não está disponível.";
        }
    }

    @Override
    public Map<String, String> search(List<String> terms) throws RemoteException {
        System.out.println("[Gateway] Método search chamado com os termos: " + terms);

        IBarrel chosen = chooseBarrel();
        if (chosen == null) return Map.of("Erro", "Nenhum Barrel disponível");

        try {
            barrels.put(chosen, System.currentTimeMillis());
            return chosen.search(terms); // delega ao barrel
        } catch (RemoteException e) {
            System.err.println("[Gateway] Erro ao consultar Barrel: " + e.getMessage());
            return Map.of("Erro", "Falha ao consultar Barrel: " + e.getMessage());
        }
    }


    @Override
    public List<String> getIncomingLinks(String url) throws RemoteException {
        System.out.println("[Gateway] Método getIncomingLinks chamado com o URL: " + url);

        IBarrel chosen = chooseBarrel();
        if (chosen == null) {
            return Collections.singletonList("Nenhum Barrel disponível");
        }

        try {
            barrels.put(chosen, System.currentTimeMillis());
            return new ArrayList<>(chosen.getIncomingLinks(url));
        } catch (RemoteException e) {
            System.err.println("[Gateway] Erro ao consultar Barrel: " + e.getMessage());
            return Collections.singletonList("Falha ao consultar Barrel: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            Gateway gateway = new Gateway();
            registry.rebind("Gateway", gateway);
            System.out.println("[Gateway] Gateway registrada no RMI Registry.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
