package downloader;

import java.rmi.Remote;
import java.rmi.RemoteException;
import common.PageData;

public interface IDownloader extends Remote {
    void takeURL(String url) throws RemoteException;
    void download(String url) throws RemoteException;
    void sendToBarrels(PageData data) throws RemoteException;
    void notifyFinished() throws RemoteException;
}
