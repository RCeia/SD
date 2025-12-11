package gateway;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import barrel.IBarrel;
import common.IClientCallback;
import common.UrlMetadata;

public interface IGateway extends Remote {
    String indexURL(String url) throws RemoteException;
    Map<String, UrlMetadata> search(List<String> terms) throws RemoteException;
    List<String> getIncomingLinks(String url) throws RemoteException;

    void registerBarrel(IBarrel barrel) throws RemoteException;
    void updateBarrelIndexSize(IBarrel barrel, int invertedSize, int incomingSize) throws RemoteException;

    void subscribe(IClientCallback client) throws RemoteException;
    void unsubscribe(IClientCallback client) throws RemoteException;
}