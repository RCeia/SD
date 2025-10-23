package gateway;

import queue.IQueue;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class Gateway extends UnicastRemoteObject implements IGateway {

    private IQueue queue; // Referência à Queue

    // Construtor agora configura a Queue diretamente no momento da criação
    public Gateway() throws RemoteException {
        super();

        // Conectar-se ao RMI Registry existente (da URLQueue)
        try {
            // Conectar-se ao RMI Registry existente (não cria um novo registry)
            Registry registry = LocateRegistry.getRegistry("localhost", 1099); // Conectar-se ao registry da URLQueue

            // Procurar pela URLQueue no RMI Registry
            queue = (IQueue) registry.lookup("URLQueueInterface"); // Nome da URLQueue registrada no registry

            System.out.println("[Gateway] URLQueue conectada com sucesso.");

        } catch (Exception e) {
            System.err.println("[Gateway] Erro ao conectar à URLQueue: " + e.getMessage());
            throw new RemoteException("Erro ao conectar à URLQueue", e);
        }
    }

    // Método para registrar a URL na Queue
    @Override
    public String indexURL(String url) throws RemoteException {
        System.out.println("[Gateway] Método indexURL chamado com o URL: " + url);

        try {
            if (queue != null) {
                queue.addURL(url);  // A URLQueue agora é responsável por imprimir a mensagem
                return "URL '" + url + "' indexado com sucesso na fila!";
            } else {
                return "Erro: Queue não está disponível.";
            }
        } catch (RemoteException e) {
            return "Erro ao indexar o URL: " + e.getMessage();
        }
    }

    // Método de busca de URL
    @Override
    public Map<String, String> search(String searchTerm) throws RemoteException {
        System.out.println("[Gateway] Método search chamado com o termo de pesquisa: " + searchTerm);

        Map<String, String> results = new HashMap<>();
        results.put("http://example.com", "Resultado encontrado para: " + searchTerm);
        return results;
    }

    // Método para consultar links para uma página
    @Override
    public List<String> getIncomingLinks(String url) throws RemoteException {
        System.out.println("[Gateway] Método getIncomingLinks chamado com o URL: " + url);

        return Arrays.asList("http://example1.com", "http://example2.com");
    }

    // Método main para inicializar a Gateway
    public static void main(String[] args) {
        try {
            // Não cria o RMI Registry, apenas se conecta ao existente
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);  // Conectar-se ao RMI Registry

            // Criar a instância da Gateway
            Gateway gateway = new Gateway();

            // Registrar a Gateway no RMI Registry (apenas se necessário)
            registry.rebind("Gateway", gateway);

            System.out.println("[Gateway] Gateway registrada no RMI Registry.");

            // Aguardar a conexão da URLQueue
            synchronized (Gateway.class) {
                System.out.println("[Gateway] Aguardando a conexão da URLQueue...");
                Gateway.class.wait();
            }

            System.out.println("[Gateway] URLQueue conectada com sucesso.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
