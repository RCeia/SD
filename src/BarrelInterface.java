import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface remota para o Storage Barrel.
 * Define os métodos acessíveis via RMI pelos Downloaders e Gateway.
 */
public interface BarrelInterface extends Remote {

    /**
     * Recebe dados de uma página (URL, título, texto, links encontrados).
     * O Downloader chama este método para enviar o resultado da análise.
     */
    void addPageData(String url, String title, String snippet,
                     Set<String> words, Set<String> outgoingLinks) throws RemoteException;

    /**
     * Pesquisa páginas que contenham todos os termos indicados.
     * Chamado pela Gateway.
     */
    List<Map<String, String>> search(List<String> terms) throws RemoteException;

    /**
     * Retorna a lista de URLs que apontam para uma página específica.
     */
    Set<String> getIncomingLinks(String url) throws RemoteException;

    /**
     * Retorna estatísticas básicas (ex: tamanho do índice).
     */
    Map<String, Object> getStats() throws RemoteException;
}
