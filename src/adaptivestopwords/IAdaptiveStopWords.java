package adaptivestopwords;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

public interface IAdaptiveStopWords extends Remote {
    void processDoc(String url, Set<String> uniqueWords) throws RemoteException;
    Set<String> getStopWords(double threshold) throws RemoteException;
}
