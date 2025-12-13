package adaptivestopwords;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementação do serviço de Stop Words Adaptativas.
 * <p>
 * Esta classe mantém estatísticas globais sobre a frequência de palavras em todos
 * os documentos processados pelo sistema. Se uma palavra aparecer em mais de uma
 * determinada percentagem de documentos (definida por {@code STOPWORD_THRESHOLD}),
 * ela passa a ser considerada uma Stop Word.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class AdaptiveStopWords extends UnicastRemoteObject implements IAdaptiveStopWords {

    /**
     * Número mínimo de documentos que devem ser processados antes de o sistema
     * começar a devolver Stop Words. Garante uma amostra estatística relevante.
     */
    private static final long MIN_DOCS_TO_LEARN = 100;

    /**
     * Limiar de frequência (0.0 a 1.0) para considerar uma palavra como Stop Word.
     * Exemplo: 0.9 significa que a palavra deve aparecer em mais de 90% dos documentos.
     */
    private static final double STOPWORD_THRESHOLD = 0.9;

    /**
     * Mapa que armazena a contagem de documentos em que cada palavra aparece.
     * Chave: Palavra, Valor: Número de documentos.
     */
    private final Map<String, Integer> docFrequency = new HashMap<>();

    /**
     * Contador total de documentos processados até ao momento.
     */
    private int processedDocs = 0;

    /**
     * Construtor da classe AdaptiveStopWords.
     *
     * @throws RemoteException Se ocorrer um erro na inicialização do objeto remoto.
     */
    public AdaptiveStopWords() throws RemoteException {
        super();
    }

    /**
     * Recebe e processa as palavras de um novo documento.
     * <p>
     * Incrementa o contador total de documentos e atualiza a frequência de ocorrência
     * de cada palavra única fornecida.
     * </p>
     *
     * @param url O URL do documento.
     * @param uniqueWords Conjunto de palavras únicas encontradas.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public synchronized void processDoc(String url, Set<String> uniqueWords) throws RemoteException {
        processedDocs++;
        for (String word : uniqueWords) {
            docFrequency.merge(word, 1, Integer::sum);
        }
        System.out.println("[AdaptiveStopWords] URL " + url + " processado. Total de documentos: " + processedDocs);
    }

    /**
     * Calcula e retorna as Stop Words com base nas estatísticas acumuladas.
     * <p>
     * Verifica a proporção de ocorrência de cada palavra em relação ao total de documentos.
     * </p>
     *
     * @return Conjunto de palavras consideradas Stop Words.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public Set<String> getStopWords() throws RemoteException {
        Set<String> stopWords = new HashSet<>();

        if (processedDocs < MIN_DOCS_TO_LEARN) {
            return stopWords; // Ainda não processou documentos suficientes
        }

        for (Map.Entry<String, Integer> entry : docFrequency.entrySet()) {
            double dfRatio = (double) entry.getValue() / processedDocs;
            if (dfRatio > STOPWORD_THRESHOLD) {
                stopWords.add(entry.getKey());
            }
        }
        System.out.println(stopWords);
        return stopWords;
    }

    /**
     * Método principal para iniciar o servidor de Stop Words.
     * <p>
     * Cria a instância do serviço, regista-a no RMI Registry e aguarda pedidos.
     * </p>
     *
     * @param args Argumentos de linha de comando: [0] Porta do registry (opcional).
     */
    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 1099;

            AdaptiveStopWords adaptiveStopWords = new AdaptiveStopWords();
            Registry registry = LocateRegistry.getRegistry("localhost", port);
            registry.rebind("AdaptiveStopWords", adaptiveStopWords);

            System.out.println("[AdaptiveStopWords] Serviço de identificação de stop words registado e pronto.");

            synchronized (AdaptiveStopWords.class) {
                AdaptiveStopWords.class.wait();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}