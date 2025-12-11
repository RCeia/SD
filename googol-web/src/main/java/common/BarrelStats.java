package common;

import java.io.Serializable;

public class BarrelStats implements Serializable {

    // Identificador para compatibilidade de serialização (boa prática, mas opcional em labs simples)
    private static final long serialVersionUID = 1L;

    // Campos da estatística
    private String name;                // Nome do Barrel (ex: "Barrel-1")
    private String status;              // Estado (ex: "Active")
    private double avgResponseTime;     // Tempo médio das respostas
    private int requestCount;           // [NOVO] Número de pesquisas em que se baseia a média
    private int invertedIndexCount;     // Tamanho do índice invertido (palavras)
    private int incomingLinksCount;     // Tamanho da lista de links (URLs)

    /**
     * Construtor completo
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

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public double getAvgResponseTime() {
        return avgResponseTime;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public int getInvertedIndexCount() {
        return invertedIndexCount;
    }

    public int getIncomingLinksCount() {
        return incomingLinksCount;
    }

    // --- Exibição ---

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