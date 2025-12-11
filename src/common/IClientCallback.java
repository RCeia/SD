package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IClientCallback extends Remote {
    // A Gateway chamará este método quando tiver novas estatísticas
    void onStatisticsUpdated(String statsOutput) throws RemoteException;
}