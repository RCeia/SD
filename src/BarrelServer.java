import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class BarrelServer extends UnicastRemoteObject implements BarrelInterface {

    public BarrelServer() throws RemoteException {
        super();
        System.out.println("[Barrel] Inicializado (modo esqueleto).");
    }

    @Override
    public void addPageData(String url, String title, String snippet,
                            Set<String> words, Set<String> outgoingLinks) throws RemoteException {
        System.out.printf("[Barrel] addPageData(url=%s, title=%s, words=%d, outLinks=%d)%n",
                url, title, words == null ? 0 : words.size(), outgoingLinks == null ? 0 : outgoingLinks.size());
    }

    @Override
    public List<Map<String, String>> search(List<String> terms) throws RemoteException {
        System.out.println("[Barrel] search: " + terms);
        // retorno simulado
        Map<String, String> fake = new HashMap<>();
        fake.put("url", "http://example.com");
        fake.put("title", "Exemplo");
        fake.put("snippet", "Snippet simulado.");
        fake.put("inboundLinks", "0");
        return Collections.singletonList(fake);
    }

    @Override
    public Set<String> getIncomingLinks(String url) throws RemoteException {
        System.out.println("[Barrel] getIncomingLinks: " + url);
        return new HashSet<>();
    }

    @Override
    public Map<String, Object> getStats() throws RemoteException {
        Map<String, Object> m = new HashMap<>();
        m.put("pages", 0);
        m.put("words", 0);
        System.out.println("[Barrel] getStats");
        return m;
    }

    public static void main(String[] args) {
        try {
            String name = args.length > 0 ? args[0] : "Barrel1";
            BarrelServer srv = new BarrelServer();
            Naming.rebind(name, srv);
            System.out.println("[Barrel] Bound no RMI como '" + name + "'");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
