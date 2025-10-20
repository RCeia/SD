package downloader;

import java.rmi.Remote;
import java.rmi.RemoteException;
import common.PageData;

public interface IDownloader extends Remote {
    void takeURL(String url) throws RemoteException;
    void notifyFinished() throws RemoteException;
}
