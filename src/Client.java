import java.rmi.Naming;
import java.util.*;

public class Client {
    public static void main(String[] args) {
        try {
            String gatewayName = args.length > 0 ? args[0] : "Gateway";
            GatewayInterface g = (GatewayInterface) Naming.lookup(gatewayName);
            System.out.println("[Client] Conectado à Gateway: " + g.ping());

            // 1) enviar um URL para indexação (via Gateway -> Queue -> notifica Downloaders)
            String url = args.length > 1 ? args[1] : "http://example.com";
            System.out.println("[Client] addUrl: " + url);
            g.addUrl(url);

            // 2) simular uma pesquisa (Gateway -> Barrel)
            List<String> terms = Arrays.asList("example", "test");
            System.out.println("[Client] search: " + terms);
            var results = g.search(terms, 1);

            System.out.println("[Client] resultados:");
            for (var m : results) {
                System.out.println(" - " + m.get("title") + " | " + m.get("url") + " | " + m.get("snippet"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
