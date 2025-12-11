package common;

import java.io.Serializable;

public class BarrelStats implements Serializable {
    private String name;
    private String status; // "Ativo", "Ocupado", etc.
    private double avgResponseTime;
    private int indexSize; // Quantidade de URLs/Termos indexados

    public BarrelStats(String name, String status, double avgResponseTime, int indexSize) {
        this.name = name;
        this.status = status;
        this.avgResponseTime = avgResponseTime;
        this.indexSize = indexSize;
    }

    // Getters
    public String getName() { return name; }
    public String getStatus() { return status; }
    public double getAvgResponseTime() { return avgResponseTime; }
    public int getIndexSize() { return indexSize; }

    @Override
    public String toString() {
        return name + " [" + status + "] - " + indexSize + " itens - " + String.format("%.2f", avgResponseTime) + "ms";
    }
}