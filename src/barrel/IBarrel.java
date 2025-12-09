package barrel;

import common.PageData;
import common.UrlMetadata;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IBarrel extends Remote {
    void storePage(PageData page) throws RemoteException;
    Map<String, String> search(List<String> terms) throws RemoteException;
    Set<String> getIncomingLinks(String url) throws RemoteException;
    boolean isActive() throws RemoteException;
    int getIndexSize() throws RemoteException;
    Map<String, Set<String>> getInvertedIndex() throws RemoteException;
    Map<String, Set<String>> getIncomingLinksMap() throws RemoteException;
    boolean isUrlInBarrel(String url) throws RemoteException;

    // return the logical name of the barrel (e.g. "Barrel357094")
    String getName() throws RemoteException;
    Map<String, UrlMetadata> getPageMetadata() throws RemoteException;
    // if you still use getSystemStats on barrel, keep it
    String getSystemStats() throws RemoteException;
}
