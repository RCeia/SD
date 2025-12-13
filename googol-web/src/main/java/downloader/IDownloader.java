package downloader;

import barrel.IBarrel;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface remota que define as operações do Downloader.
 * <p>
 * O Downloader é responsável por baixar páginas web, processar o seu conteúdo
 * e distribuir os dados extraídos para os Barrels. Esta interface permite
 * que a Queue atribua tarefas e que o sistema atualize a lista de Barrels dinamicamente.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public interface IDownloader extends Remote {

    /**
     * Recebe um URL da Queue para ser processado.
     * <p>
     * Este método é chamado remotamente pela Queue quando há trabalho disponível
     * e o Downloader está marcado como livre.
     * </p>
     *
     * @param url A string contendo o URL a ser baixado e processado.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void takeURL(String url) throws RemoteException;

    /**
     * Notifica a Queue de que o Downloader terminou o processamento atual.
     * <p>
     * Deve ser chamado após a conclusão (sucesso ou falha tratada) do download
     * para que a Queue possa atribuir um novo URL a este Downloader.
     * </p>
     *
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void notifyFinished() throws RemoteException;

    /**
     * Adiciona dinamicamente um novo Barrel à lista local do Downloader.
     * <p>
     * Permite que o Downloader tenha conhecimento de novos nós de armazenamento
     * sem necessidade de reiniciar.
     * </p>
     *
     * @param newBarrel A referência remota para o novo Barrel.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void addBarrel(IBarrel newBarrel) throws RemoteException;
}