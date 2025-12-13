package barrel;

import common.PageData;
import common.UrlMetadata;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface remota que define as operações de um servidor de armazenamento (Barrel).
 * <p>
 * O Barrel é responsável por armazenar o índice invertido, os metadados das páginas
 * e o grafo de ligações (links) entre páginas. Suporta operações de indexação,
 * pesquisa e sincronização de dados entre réplicas.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 2.0
 */
public interface IBarrel extends Remote {

    // Funcionalidades Principais

    /**
     * Armazena os dados processados de uma página no Barrel.
     * <p>
     * Atualiza o índice invertido (termos), a lista de links de entrada e
     * os metadados da página.
     * </p>
     *
     * @param page O objeto {@code PageData} contendo a informação extraída pelo Downloader.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void storePage(PageData page) throws RemoteException;

    /**
     * Realiza uma pesquisa no índice invertido.
     * <p>
     * Processa a lista de termos, interseta os resultados, ordena por relevância
     * (número de links de entrada) e aplica paginação.
     * </p>
     *
     * @param terms Lista de termos a pesquisar (pode incluir a flag de paginação [PAGE:X]).
     * @return Um mapa de URLs e respetivos metadados correspondentes à pesquisa.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    Map<String, UrlMetadata> search(List<String> terms) throws RemoteException;

    // Consultas de Links

    /**
     * Verifica se um determinado URL já foi indexado ou referenciado neste Barrel.
     *
     * @param url A string do URL a verificar.
     * @return {@code true} se o URL existir no sistema, {@code false} caso contrário.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    boolean isUrlInBarrel(String url) throws RemoteException;

    /**
     * Obtém o conjunto de URLs que apontam para um determinado URL (Incoming Links).
     *
     * @param url O URL de destino.
     * @return Um conjunto de strings contendo os URLs de origem.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    Set<String> getIncomingLinks(String url) throws RemoteException;

    // Sincronização de Dados

    /**
     * Obtém uma cópia completa do índice invertido atual.
     * <p>
     * Utilizado principalmente durante o processo de sincronização quando um novo Barrel entra na rede.
     * </p>
     *
     * @return Mapa associando termos (String) a um conjunto de URLs (Set).
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    Map<String, Set<String>> getInvertedIndex() throws RemoteException;

    /**
     * Obtém uma cópia completa do mapa de links de entrada.
     * <p>
     * Utilizado para sincronização de dados entre Barrels.
     * </p>
     *
     * @return Mapa associando um URL de destino a um conjunto de URLs de origem.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    Map<String, Set<String>> getIncomingLinksMap() throws RemoteException;

    /**
     * Obtém uma cópia completa dos metadados das páginas armazenadas.
     * <p>
     * Utilizado para sincronização de dados entre Barrels.
     * </p>
     *
     * @return Mapa associando URLs aos seus metadados (Título, Citação).
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    Map<String, UrlMetadata> getPageMetadata() throws RemoteException;

    // Gestão e Estado

    /**
     * Obtém o nome identificador do Barrel.
     *
     * @return O nome do Barrel (ex: "Barrel-1234").
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    String getName() throws RemoteException;

    /**
     * Verifica se o Barrel está ativo e pronto para receber pedidos.
     *
     * @return {@code true} se o Barrel estiver operacional (ACTIVE), {@code false} se estiver a sincronizar.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    boolean isActive() throws RemoteException;

    /**
     * Obtém o tamanho atual do índice invertido (número de palavras chave indexadas).
     *
     * @return O número total de entradas no índice.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    int getIndexSize() throws RemoteException;
}