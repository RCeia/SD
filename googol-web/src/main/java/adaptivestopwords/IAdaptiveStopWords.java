package adaptivestopwords;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

/**
 * Interface remota que define o serviço de identificação adaptativa de Stop Words.
 * <p>
 * Este serviço analisa a frequência de palavras nos documentos processados
 * para identificar dinamicamente quais devem ser consideradas "Stop Words"
 * (palavras muito comuns que podem ser ignoradas nas pesquisas).
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public interface IAdaptiveStopWords extends Remote {

    /**
     * Processa um documento contabilizando a frequência das suas palavras únicas.
     * <p>
     * Deve ser chamado pelo Downloader para cada página processada, permitindo
     * que o serviço atualize as suas estatísticas globais.
     * </p>
     *
     * @param url O URL do documento processado.
     * @param uniqueWords Conjunto de palavras únicas encontradas no documento.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    void processDoc(String url, Set<String> uniqueWords) throws RemoteException;

    /**
     * Obtém o conjunto atual de Stop Words identificadas pelo sistema.
     * <p>
     * Retorna um conjunto vazio se ainda não tiverem sido processados documentos
     * suficientes (conforme definido pelo limiar de aprendizagem).
     * </p>
     *
     * @return Conjunto de strings contendo as palavras classificadas como Stop Words.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    Set<String> getStopWords() throws RemoteException;
}