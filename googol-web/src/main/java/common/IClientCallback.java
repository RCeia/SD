package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IClientCallback extends Remote {
    // Agora recebe o objeto complexo em vez de String
    void onStatisticsUpdated(SystemStatistics stats) throws RemoteException;
}