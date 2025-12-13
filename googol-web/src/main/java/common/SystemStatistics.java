package common;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Classe de dados (DTO) que agrega as estatísticas globais do sistema.
 * <p>
 * Este objeto é enviado do Gateway para os Clientes subscritos (via RMI)
 * para atualizar dashboards ou interfaces de monitorização.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class SystemStatistics implements Serializable {

    /**
     * Mapa contendo os termos mais pesquisados e a respetiva frequência.
     */
    private Map<String, Integer> topSearchTerms;

    /**
     * Mapa contendo os URLs mais consultados nos resultados e a respetiva frequência.
     */
    private Map<String, Integer> topConsultedUrls;

    /**
     * Lista contendo os detalhes estatísticos individuais de cada Barrel ativo.
     */
    private List<BarrelStats> barrelDetails;

    /**
     * Construtor da classe SystemStatistics.
     *
     * @param topSearchTerms Mapa dos termos mais pesquisados.
     * @param topConsultedUrls Mapa dos URLs mais clicados/consultados.
     * @param barrelDetails Lista de objetos BarrelStats com o estado de cada nó.
     */
    public SystemStatistics(Map<String, Integer> topSearchTerms,
                            Map<String, Integer> topConsultedUrls,
                            List<BarrelStats> barrelDetails) {
        this.topSearchTerms = topSearchTerms;
        this.topConsultedUrls = topConsultedUrls;
        this.barrelDetails = barrelDetails;
    }

    // Getters

    /**
     * Obtém o mapa dos termos mais pesquisados.
     *
     * @return Mapa (Termo -> Frequência).
     */
    public Map<String, Integer> getTopSearchTerms() { return topSearchTerms; }

    /**
     * Obtém o mapa dos URLs mais consultados.
     *
     * @return Mapa (URL -> Frequência).
     */
    public Map<String, Integer> getTopConsultedUrls() { return topConsultedUrls; }

    /**
     * Obtém a lista de estatísticas detalhadas por Barrel.
     *
     * @return Lista de objetos BarrelStats.
     */
    public List<BarrelStats> getBarrelDetails() { return barrelDetails; }
}