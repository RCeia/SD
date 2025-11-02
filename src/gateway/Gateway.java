package gateway;

import queue.IQueue;
import barrel.IBarrel;
import common.RetryLogic;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.ConnectException;

public class Gateway extends UnicastRemoteObject implements IGateway {

    private final IQueue queue;
    private final Map<IBarrel, Long> barrels;
    private final Map<IBarrel, List<Long>> responseTimes;
    private final Map<String, Integer> termFrequency;
    private final Map<String, Integer> urlFrequency;
    private final Random random;

    public Gateway() throws RemoteException {
        super();
        this.barrels = new HashMap<>();
        this.responseTimes = new HashMap<>();
        this.termFrequency = new HashMap<>();
        this.urlFrequency = new HashMap<>();
        this.random = new Random();

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            queue = (IQueue) registry.lookup("URLQueueInterface");
            System.out.println("[Gateway] URLQueue conectada com sucesso.");

            for (String name : registry.list()) {
                if (name.startsWith("Barrel")) {
                    IBarrel barrel = (IBarrel) registry.lookup(name);
                    barrels.put(barrel, 0L);
                    responseTimes.put(barrel, new ArrayList<>());
                    System.out.println("[Gateway] Barrel encontrado e registado: " + name);
                }
            }

        } catch (Exception e) {
            System.err.println("[Gateway] Erro ao conectar ao RMI Registry: " + e.getMessage());
            throw new RemoteException("Erro ao conectar ao RMI Registry", e);
        }
    }

    private IBarrel chooseBarrel() {
        synchronized (barrels) {
            if (barrels.isEmpty()) return null;

            boolean allHaveStats = responseTimes.values().stream()
                    .allMatch(list -> list != null && !list.isEmpty());

            if (allHaveStats) {
                IBarrel bestBarrel = null;
                double bestAvg = Double.MAX_VALUE;
                for (Map.Entry<IBarrel, List<Long>> entry : responseTimes.entrySet()) {
                    double avg = entry.getValue().stream().mapToLong(Long::longValue).average().orElse(Double.MAX_VALUE);
                    if (avg < bestAvg) {
                        bestAvg = avg;
                        bestBarrel = entry.getKey();
                    }
                }
                if (bestBarrel != null) {
                    System.out.println("[Gateway] Barrel escolhido (menor tempo médio): " + extractBarrelName(bestBarrel));
                    return bestBarrel;
                }
            }

            List<IBarrel> neverUsed = new ArrayList<>();
            long oldestTime = Long.MAX_VALUE;
            IBarrel oldestBarrel = null;
            for (Map.Entry<IBarrel, Long> entry : barrels.entrySet()) {
                long lastUsed = entry.getValue();
                if (lastUsed == 0) neverUsed.add(entry.getKey());
                else if (lastUsed < oldestTime) {
                    oldestTime = lastUsed;
                    oldestBarrel = entry.getKey();
                }
            }

            if (!neverUsed.isEmpty()) return neverUsed.get(random.nextInt(neverUsed.size()));
            return oldestBarrel;
        }
    }

    private boolean tryReconnect(String barrelName) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            IBarrel newBarrel = (IBarrel) registry.lookup(barrelName);
            synchronized (barrels) {
                barrels.put(newBarrel, 0L);
                responseTimes.put(newBarrel, new ArrayList<>());
            }
            System.out.println("[Gateway] Reconexão bem-sucedida ao " + barrelName);
            return true;
        } catch (Exception e) {
            System.err.println("[Gateway] Falha ao reconectar ao " + barrelName + ": " + e.getMessage());
            return false;
        }
    }


    @Override
    public String indexURL(String url) throws RemoteException {
        if (queue != null) {
            queue.addURL(url);
            return "URL '" + url + "' indexado com sucesso!";
        }
        return "Erro: Queue não disponível.";
    }

    @Override
    public Map<String, String> search(List<String> terms) throws RemoteException {
        synchronized (barrels) {
            while (!barrels.isEmpty()) {
                IBarrel chosen = chooseBarrel();
                if (chosen == null) return Map.of("Erro", "Ocorreu um erro na pesquisa");

                try {
                    barrels.put(chosen, System.currentTimeMillis());
                    long start = System.currentTimeMillis();

                    String barrelName = extractBarrelName(chosen);
                    Map<String, String> result = RetryLogic.executeWithRetry(
                            3,                     // 3 tentativas
                            2000,                  // delay 2 segundos
                            () -> tryReconnect(barrelName),  // ação de reconexão
                            () -> chosen.search(terms)       // operação remota
                    );

                    long elapsed = System.currentTimeMillis() - start;
                    updateInternalStats(chosen, terms, Collections.emptyList(), elapsed);

                    // ✅ Sort results by importance (number of incoming links)
                    Map<String, String> sorted = sortByIncomingLinks(result.keySet(), chosen);
                    return sorted;

                } catch (RemoteException e) {
                    if (isConnectionRefused(e)) {
                        System.out.println("[Gateway] Barrel inativo removido: " + extractBarrelName(chosen));
                        barrels.remove(chosen);
                        responseTimes.remove(chosen);
                        continue;
                    }
                    throw e;
                }
            }
        }
        return Map.of("Erro", "Nenhum Barrel ativo disponível");
    }

    @Override
    public List<String> getIncomingLinks(String url) throws RemoteException {
        synchronized (barrels) {
            while (!barrels.isEmpty()) {
                IBarrel chosen = chooseBarrel();
                if (chosen == null) return List.of("Nenhum Barrel ativo disponível");

                try {
                    barrels.put(chosen, System.currentTimeMillis());
                    long start = System.currentTimeMillis();

                    String barrelName = extractBarrelName(chosen);
                    Collection<String> rawLinks = RetryLogic.executeWithRetry(
                            3,
                            2000,
                            () -> tryReconnect(barrelName),
                            () -> chosen.getIncomingLinks(url)
                    );

                    List<String> links = new ArrayList<>(rawLinks);

                    long elapsed = System.currentTimeMillis() - start;
                    updateInternalStats(chosen, Collections.emptyList(), List.of(url), elapsed);

                    // ✅ Sort links by importance (descending number of incoming links)
                    links.sort((a, b) -> {
                        try {
                            int countA = chosen.getIncomingLinks(a).size();
                            int countB = chosen.getIncomingLinks(b).size();
                            return Integer.compare(countB, countA);
                        } catch (RemoteException e) {
                            return 0;
                        }
                    });
                    return links;

                } catch (RemoteException e) {
                    if (isConnectionRefused(e)) {
                        System.out.println("[Gateway] Barrel inativo removido: " + extractBarrelName(chosen));
                        barrels.remove(chosen);
                        responseTimes.remove(chosen);
                        continue;
                    }
                    throw e;
                }
            }
        }
        return List.of("Nenhum Barrel ativo disponível");
    }

    // ✅ New helper method: sorts URLs by number of incoming links (importance)
    private Map<String, String> sortByIncomingLinks(Collection<String> urls, IBarrel barrel) {
        List<String> sortedUrls = new ArrayList<>(urls);
        sortedUrls.sort((a, b) -> {
            try {
                int countA = barrel.getIncomingLinks(a).size();
                int countB = barrel.getIncomingLinks(b).size();
                return Integer.compare(countB, countA);
            } catch (RemoteException e) {
                return 0;
            }
        });

        Map<String, String> sorted = new LinkedHashMap<>();
        for (String url : sortedUrls) {
            try {
                sorted.put(url, String.valueOf(barrel.getIncomingLinks(url).size()) + " incoming links");
            } catch (RemoteException e) {
                sorted.put(url, "Erro ao obter ligações");
            }
        }
        return sorted;
    }

    @Override
    public String getSystemStats() throws RemoteException {
        StringBuilder sb = new StringBuilder();
        sb.append("===== Estatísticas do Sistema =====\n\n");

        sb.append("=== Top 10 Termos Mais Pesquisados ===\n");
        printTop10(termFrequency, sb);
        sb.append("\n=== Top 10 URLs Mais Consultadas ===\n");
        printTop10(urlFrequency, sb);

        sb.append("\n=== Estatísticas de Cada Barrel (Ativos) ===\n\n");

        boolean found = false;
        synchronized (barrels) {
            List<IBarrel> toRemove = new ArrayList<>();
            for (IBarrel barrel : barrels.keySet()) {
                try {
                    String stats = barrel.getSystemStats();
                    if (stats == null || stats.isEmpty()) continue;

                    double avg = getAverageResponseTime(barrel);
                    int count = responseTimes.getOrDefault(barrel, List.of()).size();

                    sb.append(stats.trim()).append("\n")
                            .append("Tempo médio de resposta: ")
                            .append(String.format("%.2f ms (baseado em %d consultas)", avg, count))
                            .append("\n\n");
                    found = true;

                } catch (RemoteException e) {
                    if (isConnectionRefused(e)) toRemove.add(barrel);
                }
            }
            for (IBarrel b : toRemove) {
                System.out.println("[Gateway] Barrel inativo removido (estatísticas): " + extractBarrelName(b));
                barrels.remove(b);
                responseTimes.remove(b);
            }
        }

        if (!found) sb.append("(Nenhum barrel ativo disponível)\n");
        return sb.toString();
    }

    private void updateInternalStats(IBarrel barrel, List<String> terms, List<String> urls, long elapsed) {
        for (String t : terms) termFrequency.put(t, termFrequency.getOrDefault(t, 0) + 1);
        for (String u : urls) urlFrequency.put(u, urlFrequency.getOrDefault(u, 0) + 1);
        responseTimes.computeIfAbsent(barrel, k -> new ArrayList<>()).add(elapsed);
    }

    private void printTop10(Map<String, Integer> map, StringBuilder sb) {
        if (map.isEmpty()) {
            sb.append("Sem dados disponíveis.\n");
            return;
        }
        map.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sb.append(e.getKey()).append(" -> ").append(e.getValue()).append(" vezes\n"));
    }

    private double getAverageResponseTime(IBarrel barrel) {
        List<Long> times = responseTimes.get(barrel);
        if (times == null || times.isEmpty()) return 0.0;
        return times.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private boolean isConnectionRefused(RemoteException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConnectException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private String extractBarrelName(IBarrel barrel) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            for (String name : registry.list()) {
                try {
                    if (registry.lookup(name).equals(barrel)) return name;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return "BarrelDesconhecido";
    }
    @Override
    public synchronized void registerBarrel(IBarrel barrel) throws RemoteException {
        if (!barrels.containsKey(barrel)) {
            barrels.put(barrel, 0L);
            responseTimes.put(barrel, new ArrayList<>());
            System.out.println("[Gateway] Novo Barrel registado dinamicamente: " + extractBarrelName(barrel));
        } else {
            System.out.println("[Gateway] Barrel já estava registado: " + extractBarrelName(barrel));
        }
    }


    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            Gateway gateway = new Gateway();
            registry.rebind("Gateway", gateway);
            System.out.println("[Gateway] Gateway registrada no RMI Registry.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
