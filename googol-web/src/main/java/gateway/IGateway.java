package gateway;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import barrel.IBarrel;
import common.IClientCallback;
import common.UrlMetadata;

/**
 * Interface remota que define as operações do Gateway de pesquisa.
 * <p>
 * O Gateway atua como o ponto de entrada principal do sistema, recebendo pedidos
 * dos clientes (pesquisa, indexação, administração) e coordenando os Barrels.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 2.0
 */
public interface IGateway extends Remote {

    /**
     * Solicita a indexação de um novo URL no sistema.
     *
     * @param url A string do URL a ser indexado.
     * @return Uma mensagem de confirmação ou status sobre o início da indexação.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    String indexURL(String url) throws RemoteException;

    /**
     * Realiza uma pesquisa no sistema baseada numa lista de termos.
     *
     * @param terms Lista de palavras-chave para a pesquisa.
     * @return Um mapa contendo os URLs encontrados (chaves) e os seus respetivos metadados (valores).
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    Map<String, UrlMetadata> search(List<String> terms) throws RemoteException;

    /**
     * Obtém a lista de URLs que contêm referências (links) para um URL específico.
     *
     * @param url O URL de destino para o qual se procuram referências.
     * @return Uma lista de strings contendo os URLs que apontam para o URL fornecido.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    List<String> getIncomingLinks(String url) throws RemoteException;

    /**
     * Regista um novo servidor de armazenamento (Barrel) no Gateway.
     *
     * @param barrel A referência remota para o Barrel a ser registado.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void registerBarrel(IBarrel barrel) throws RemoteException;

    /**
     * Atualiza as informações de carga/tamanho dos índices de um Barrel específico.
     * Utilizado para balanceamento de carga e monitorização.
     *
     * @param barrel A referência remota do Barrel que está a reportar.
     * @param invertedSize O tamanho atual do índice invertido no Barrel.
     * @param incomingSize O tamanho atual do índice de links de entrada no Barrel.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void updateBarrelIndexSize(IBarrel barrel, int invertedSize, int incomingSize) throws RemoteException;

    /**
     * Subscreve um cliente para receber notificações assíncronas (callbacks) do Gateway.
     * Geralmente utilizado para painéis de administração ou atualizações de estado em tempo real.
     *
     * @param client A referência para a interface de callback do cliente.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void subscribe(IClientCallback client) throws RemoteException;

    /**
     * Remove a subscrição de um cliente, cessando o envio de notificações.
     *
     * @param client A referência para a interface de callback do cliente a remover.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void unsubscribe(IClientCallback client) throws RemoteException;
}