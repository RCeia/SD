package queue;

import downloader.IDownloader;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Interface remota que define as operações da fila de URLs (Queue).
 * <p>
 * Esta interface permite adicionar URLs à fila, obter URLs para processamento
 * e gerir o registo e disponibilidade dos Downloaders via RMI.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public interface IQueue extends Remote {

    /**
     * Adiciona um único URL à fila de processamento.
     *
     * @param url A string contendo o URL a ser adicionado.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void addURL(String url) throws RemoteException;

    /**
     * Adiciona uma lista de URLs à fila de processamento.
     *
     * @param urls Lista de strings contendo os URLs a serem adicionados.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void addURLs(List<String> urls) throws RemoteException;

    /**
     * Retira e retorna o próximo URL da fila para ser processado.
     *
     * @return A string do próximo URL, ou null se a fila estiver vazia.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    String getNextURL() throws RemoteException;

    /**
     * Obtém o número total de URLs atualmente na fila.
     *
     * @return O tamanho atual da fila.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    int getQueueSize() throws RemoteException;

    /**
     * Regista um novo Downloader no sistema para receber tarefas.
     *
     * @param downloader A referência para a interface remota do Downloader.
     * @param id O identificador único do Downloader.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void registerDownloader(IDownloader downloader, int id) throws RemoteException;

    /**
     * Notifica a fila de que um Downloader específico concluiu a tarefa e está pronto para receber outra.
     *
     * @param downloader A referência para a interface remota do Downloader disponível.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void notifyDownloaderAvailable(IDownloader downloader) throws RemoteException;
}