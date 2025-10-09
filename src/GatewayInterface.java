import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface GatewayInterface extends Remote {
    void addUrl(String url) throws RemoteException;
    List<Map<String, String>> search(List<String> terms, int page) throws RemoteException;
    String ping() throws RemoteException;
}
