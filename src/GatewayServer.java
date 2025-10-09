import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class GatewayServer extends UnicastRemoteObject implements GatewayInterface {

    // nomes RMI esperados
    private final String queueName;
    private final List<String> barrelNames;

    private URLQueueInterface queue;
    private final Random rnd = new Random();

    public GatewayServer(String queueName, List<String> barrelNames) throws RemoteException {
        super();
        this.queueName = queueName;
        this.barrelNames = barrelNames;
        System.out.println("[Gateway] Inicializado (modo esqueleto).");
        lookupRemotes();
    }

    private void lookupRemotes() {
        try {
            this.queue = (URLQueueInterface) Naming.lookup(queueName);
            System.out.println("[Gateway] Conectado à Queue: " + queueName);
        } catch (Exception e) {
            System.out.println("[Gateway] Aviso: não consegui ligar à Queue ainda: " + e.getMessage());
        }
    }

    private BarrelInterface pickBarrel() {
        for (int i = 0; i < barrelNames.size(); i++) {
            String name = barrelNames.get(rnd.nextInt(barrelNames.size()));
            try {
                BarrelInterface b = (BarrelInterface) Naming.lookup(name);
                return b;
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public void addUrl(String url) throws RemoteException {
        System.out.println("[Gateway] addUrl: " + url);
        try {
            if (queue == null) lookupRemotes();
            if (queue != null) queue.addUrl(url);
            else System.out.println("[Gateway] Queue indisponível.");
        } catch (Exception e) {
            System.out.println("[Gateway] Falha ao enviar URL à Queue: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, String>> search(List<String> terms, int page) throws RemoteException {
        System.out.printf("[Gateway] search terms=%s page=%d%n", terms, page);
        BarrelInterface b = pickBarrel();
        if (b == null) {
            System.out.println("[Gateway] Nenhum Barrel disponível.");
            return Collections.emptyList();
        }
        try {
            // sem paginação real aqui — apenas passa direto
            return b.search(terms);
        } catch (Exception e) {
            System.out.println("[Gateway] Erro ao consultar Barrel: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public String ping() throws RemoteException {
        return "Gateway up";
    }
    public static void main(String[] args) {
        try {
            String bindName = args.length > 0 ? args[0] : "Gateway";
            String queueName = args.length > 1 ? args[1] : "Queue";
            // pode passar múltiplos barrels: Gateway Barrel1 Barrel2 ...
            List<String> barrels = new ArrayList<>();
            if (args.length > 2) {
                barrels.addAll(Arrays.asList(args).subList(2, args.length));
            } else {
                barrels.add("Barrel1");
            }
            GatewayServer g = new GatewayServer(queueName, barrels);
            Naming.rebind(bindName, g);
            System.out.println("[Gateway] Bound no RMI como '" + bindName + "'");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
