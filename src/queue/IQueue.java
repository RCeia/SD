package queue;

import downloader.IDownloader;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IQueue extends Remote {
    void addURL(String url) throws RemoteException;
    void addURLs(List<String> urls) throws RemoteException;
    String getNextURL() throws RemoteException;
    int getQueueSize() throws RemoteException;
    void registerDownloader(IDownloader downloader, int id) throws RemoteException;
    void notifyDownloaderAvailable(IDownloader downloader) throws RemoteException;
}
