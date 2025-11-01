package gateway;

import queue.IQueue;
import barrel.IBarrel;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.ConnectException;
import java.util.regex.Pattern;

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
        if (barrels.isEmpty()) {
            System.err.println("[Gateway] Nenhum Barrel disponível!");
            return null;
        }

        List<IBarrel> neverUsed = new ArrayList<>();
        long oldestTime = Long.MAX_VALUE;
        IBarrel oldestBarrel = null;

        for (Map.Entry<IBarrel, Long> entry : barrels.entrySet()) {
            long lastUsed = entry.getValue();
            if (lastUsed == 0) {
                neverUsed.add(entry.getKey());
            } else if (lastUsed < oldestTime) {
                oldestTime = lastUsed;
                oldestBarrel = entry.getKey();
            }
        }

        if (!neverUsed.isEmpty()) {
            return neverUsed.get(random.nextInt(neverUsed.size()));
        }
        return oldestBarrel;
    }

    @Override
    public String indexURL(String url) throws RemoteException {
        System.out.println("[Gateway] Método indexURL chamado com o URL: " + url);

        if (queue != null) {
            queue.addURL(url);
            return "URL '" + url + "' indexado com sucesso na fila!";
        } else {
            return "Erro: Queue não está disponível.";
        }
    }

    @Override
    public Map<String, String> search(List<String> terms) throws RemoteException {
        System.out.println("[Gateway] Método search chamado com os termos: " + terms);

        IBarrel chosen = chooseBarrel();
        if (chosen == null) return Map.of("Erro", "Nenhum Barrel disponível");

        long start = System.currentTimeMillis();
        try {
            barrels.put(chosen, System.currentTimeMillis());
            Map<String, String> result = chosen.search(terms);
            long elapsed = System.currentTimeMillis() - start;
            updateInternalStats(chosen, terms, Collections.emptyList(), elapsed);
            return result;

        } catch (RemoteException e) {
            System.err.println("[Gateway] Erro ao consultar Barrel: " + e.getMessage());
            return Map.of("Erro", "Falha ao consultar Barrel: " + e.getMessage());
        }
    }

    @Override
    public List<String> getIncomingLinks(String url) throws RemoteException {
        System.out.println("[Gateway] Método getIncomingLinks chamado com o URL: " + url);

        IBarrel chosen = chooseBarrel();
        if (chosen == null) return List.of("Nenhum Barrel disponível");

        long start = System.currentTimeMillis();
        try {
            barrels.put(chosen, System.currentTimeMillis());
            List<String> links = new ArrayList<>(chosen.getIncomingLinks(url));
            long elapsed = System.currentTimeMillis() - start;
            updateInternalStats(chosen, Collections.emptyList(), List.of(url), elapsed);
            return links;

        } catch (RemoteException e) {
            System.err.println("[Gateway] Erro ao consultar Barrel: " + e.getMessage());
            return List.of("Falha ao consultar Barrel: " + e.getMessage());
        }
    }

    private void updateInternalStats(IBarrel barrel, List<String> terms, List<String> urls, long elapsed) {
        synchronized (termFrequency) {
            for (String term : terms) {
                termFrequency.put(term, termFrequency.getOrDefault(term, 0) + 1);
            }
        }
        synchronized (urlFrequency) {
            for (String url : urls) {
                urlFrequency.put(url, urlFrequency.getOrDefault(url, 0) + 1);
            }
        }
        synchronized (responseTimes) {
            responseTimes.computeIfAbsent(barrel, k -> new ArrayList<>()).add(elapsed);
        }
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

        boolean foundActive = false;

        for (Map.Entry<IBarrel, Long> entry : barrels.entrySet()) {
            IBarrel barrel = entry.getKey();
            String barrelName = extractBarrelName(barrel);

            try {
                String stats = barrel.getSystemStats();

                if (stats == null || stats.isEmpty()) {
                    continue; // ignora se não há estatísticas
                }

                double avg = getAverageResponseTime(barrel);
                int count = responseTimes.getOrDefault(barrel, List.of()).size();

                sb.append(stats.trim()).append("\n")
                        .append("Tempo médio de resposta: ")
                        .append(String.format("%.2f ms (baseado em %d consultas)", avg, count))
                        .append("\n\n");

                foundActive = true;

            } catch (RemoteException e) {
                if (isConnectionRefused(e)) {
                    // ignora totalmente barrels inativos
                    continue;
                } else {
                    sb.append("[").append(barrelName).append("] -> Erro ao obter estatísticas.\n\n");
                }
            }
        }

        if (!foundActive) {
            sb.append("(Nenhum barrel ativo disponível)\n");
        }

        return sb.toString();
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

    /**
     * Extrai o nome do Barrel a partir da referência RMI.
     * Usa a lista do registry em vez do toString().
     */
    private String extractBarrelName(IBarrel barrel) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            for (String name : registry.list()) {
                try {
                    if (registry.lookup(name).equals(barrel)) {
                        return name;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return "BarrelDesconhecido";
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
