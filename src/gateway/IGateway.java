package gateway;

import queue.IQueue;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.List;

public interface IGateway extends Remote {

    String indexURL(String url) throws RemoteException;

    Map<String, String> search(String searchTerm) throws RemoteException;

    List<String> getIncomingLinks(String url) throws RemoteException;
}
