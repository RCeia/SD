package com.googol.web.downloader;

import barrel.IBarrel;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IDownloader extends Remote {
    void takeURL(String url) throws RemoteException;
    void notifyFinished() throws RemoteException;
    void addBarrel(IBarrel newBarrel) throws RemoteException;
}
