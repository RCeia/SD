package gateway;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.List;

public interface IGateway extends Remote {

    // Método para indexar um novo URL
    String indexURL(String url) throws RemoteException;

    // Método para realizar a pesquisa
    Map<String, String> search(String searchTerm) throws RemoteException;

    // Método para consultar links para uma página
    List<String> getIncomingLinks(String url) throws RemoteException;
}
