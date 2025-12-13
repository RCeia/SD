package common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface remota para callback de clientes.
 * <p>
 * Permite que o Gateway envie atualizações assíncronas para os clientes
 * (por exemplo, aplicações Spring Boot ou consolas administrativas)
 * quando as estatísticas do sistema são alteradas.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public interface IClientCallback extends Remote {

    /**
     * Notifica o cliente com um novo snapshot das estatísticas do sistema.
     *
     * @param stats Objeto {@code SystemStatistics} contendo os dados atualizados.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void onStatisticsUpdated(SystemStatistics stats) throws RemoteException;
}