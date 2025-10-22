package gateway;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class Gateway extends UnicastRemoteObject implements IGateway {

    public Gateway() throws RemoteException {
        super();
    }

    @Override
    public String indexURL(String url) throws RemoteException {
        // Simulação de indexação de URL
        System.out.println("[Gateway] Método indexURL chamado com o URL: " + url);
        return "Indexação do URL '" + url + "' concluída com sucesso!";
    }

    @Override
    public Map<String, String> search(String searchTerm) throws RemoteException {
        // Simulação de pesquisa
        System.out.println("[Gateway] Método search chamado com o termo de pesquisa: " + searchTerm);
        Map<String, String> results = new HashMap<>();
        results.put("http://example.com", "Resultado encontrado para: " + searchTerm);
        return results;
    }

    @Override
    public List<String> getIncomingLinks(String url) throws RemoteException {
        // Simulação de consulta de links
        System.out.println("[Gateway] Método getIncomingLinks chamado com o URL: " + url);
        return Arrays.asList("http://example1.com", "http://example2.com");
    }

    // Método main para inicializar o RMI Registry e a Gateway
    public static void main(String[] args) {
        try {
            // Criar RMI Registry
            java.rmi.registry.Registry registry = java.rmi.registry.LocateRegistry.createRegistry(1099);

            // Criar a instância da Gateway
            Gateway gateway = new Gateway();

            // Registrar a Gateway no RMI Registry
            registry.rebind("Gateway", gateway);

            System.out.println("Gateway está pronta e registrada no RMI Registry.");

            // Manter a Gateway ativa
            synchronized (Gateway.class) {
                Gateway.class.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
