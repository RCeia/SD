package gateway;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import barrel.IBarrel;

public interface IGateway extends Remote {
    String indexURL(String url) throws RemoteException;
    Map<String, String> search(List<String> terms) throws RemoteException;
    List<String> getIncomingLinks(String url) throws RemoteException;
    String getSystemStats() throws RemoteException;
    void registerBarrel(IBarrel barrel) throws RemoteException;

}
