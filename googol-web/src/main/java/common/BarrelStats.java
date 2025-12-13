package common;

import java.io.Serializable;

/**
 * Classe que encapsula as métricas de desempenho e estado de um único Barrel.
 * <p>
 * Utilizada para transportar informações como carga, tamanho dos índices e
 * tempos de resposta do Barrel para o Gateway e posteriormente para os clientes.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class BarrelStats implements Serializable {

    /**
     * Identificador para compatibilidade de serialização.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Nome identificador do Barrel (ex: "Barrel-1").
     */
    private String name;                // Nome do Barrel (ex: "Barrel-1")

    /**
     * Estado atual do Barrel (ex: "Active", "Inactive").
     */
    private String status;              // Estado (ex: "Active")

    /**
     * Tempo médio de resposta do Barrel em milissegundos.
     */
    private double avgResponseTime;     // Tempo médio das respostas

    /**
     * Número total de pesquisas utilizadas para calcular a média de tempo.
     */
    private int requestCount;           // [NOVO] Número de pesquisas em que se baseia a média

    /**
     * Número de palavras armazenadas no índice invertido deste Barrel.
     */
    private int invertedIndexCount;     // Tamanho do índice invertido (palavras)

    /**
     * Número de URLs conhecidos na lista de links de entrada.
     */
    private int incomingLinksCount;     // Tamanho da lista de links (URLs)

    /**
     * Construtor completo para inicializar todas as métricas do Barrel.
     *
     * @param name Nome do Barrel.
     * @param status Estado de atividade.
     * @param avgResponseTime Tempo médio de resposta.
     * @param requestCount Número de requisições processadas.
     * @param invertedIndexCount Tamanho do índice invertido.
     * @param incomingLinksCount Tamanho do índice de links.
     */
    public BarrelStats(String name, String status, double avgResponseTime, int requestCount, int invertedIndexCount, int incomingLinksCount) {
        this.name = name;
        this.status = status;
        this.avgResponseTime = avgResponseTime;
        this.requestCount = requestCount;
        this.invertedIndexCount = invertedIndexCount;
        this.incomingLinksCount = incomingLinksCount;
    }

    // --- Getters ---

    /**
     * Obtém o nome do Barrel.
     *
     * @return String com o nome.
     */
    public String getName() {
        return name;
    }

    /**
     * Obtém o estado atual do Barrel.
     *
     * @return String indicando o status.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Obtém o tempo médio de resposta.
     *
     * @return Valor double em milissegundos.
     */
    public double getAvgResponseTime() {
        return avgResponseTime;
    }

    /**
     * Obtém a contagem de requisições processadas.
     *
     * @return Número inteiro de requisições.
     */
    public int getRequestCount() {
        return requestCount;
    }

    /**
     * Obtém o tamanho do índice invertido (número de palavras).
     *
     * @return Número inteiro de entradas no índice.
     */
    public int getInvertedIndexCount() {
        return invertedIndexCount;
    }

    /**
     * Obtém o tamanho do índice de links de entrada.
     *
     * @return Número inteiro de URLs registados.
     */
    public int getIncomingLinksCount() {
        return incomingLinksCount;
    }

    // --- Exibição ---

    /**
     * Retorna uma representação textual formatada das estatísticas do Barrel.
     * Útil para logs ou exibição em consola.
     *
     * @return String formatada com os detalhes do Barrel.
     */
    @Override
    public String toString() {
        // Formato: "Nome [Status] -> Detalhes... -> Tempo Médio: Xms (baseado em Y pesquisas)"
        return String.format("%s [%s]\n" +
                        "   -> Palavras Indexadas: %d\n" +
                        "   -> URLs Conhecidos: %d\n" +
                        "   -> Tempo Médio: %.2fms (baseado em %d pesquisas)",
                name,
                status,
                invertedIndexCount,
                incomingLinksCount,
                avgResponseTime,
                requestCount);
    }
}