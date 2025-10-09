import java.rmi.Remote;
import java.rmi.RemoteException;

public interface URLQueueInterface extends Remote {

    void addUrl(String url) throws RemoteException;

    void registerDownloader(DownloaderCallback downloader) throws RemoteException;

    String getNextUrl() throws RemoteException;

    int size() throws RemoteException;

    String ping() throws RemoteException;

    // Callback para notificação
    public static interface DownloaderCallback extends Remote {
        String getId() throws RemoteException;
        void notifyUrlAvailable() throws RemoteException;
    }
}
