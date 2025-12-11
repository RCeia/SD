package common;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class SystemStatistics implements Serializable {
    // Top 10 pesquisas e URLs
    private Map<String, Integer> topSearchTerms;
    private Map<String, Integer> topConsultedUrls;

    // Lista com o estado de cada Barrel
    private List<BarrelStats> barrelDetails;

    public SystemStatistics(Map<String, Integer> topSearchTerms,
                            Map<String, Integer> topConsultedUrls,
                            List<BarrelStats> barrelDetails) {
        this.topSearchTerms = topSearchTerms;
        this.topConsultedUrls = topConsultedUrls;
        this.barrelDetails = barrelDetails;
    }

    // Getters
    public Map<String, Integer> getTopSearchTerms() { return topSearchTerms; }
    public Map<String, Integer> getTopConsultedUrls() { return topConsultedUrls; }
    public List<BarrelStats> getBarrelDetails() { return barrelDetails; }
}