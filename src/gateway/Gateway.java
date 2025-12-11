package gateway;

import common.UrlMetadata;
import queue.IQueue;
import barrel.IBarrel;
import common.RetryLogic;
import common.SystemStatistics;
import common.BarrelStats;
import common.IClientCallback; // [NOVO] Importar a interface de callback

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

    // Estatísticas Globais
    private SystemStatistics currentStats;
    // Mapas separados para os dois tipos de tamanho
    private final Map<IBarrel, Integer> barrelInvertedSizes;
    private final Map<IBarrel, Integer> barrelIncomingSizes;

    // [NOVO] Lista de clientes subscritos para notificações em tempo real
    private final List<IClientCallback> subscribedClients;

    public Gateway() throws RemoteException {
        super();
        this.barrels = new HashMap<>();
        this.responseTimes = new HashMap<>();
        this.termFrequency = new HashMap<>();
        this.urlFrequency = new HashMap<>();
        this.barrelInvertedSizes = new HashMap<>();
        this.barrelIncomingSizes = new HashMap<>();
        this.subscribedClients = new ArrayList<>(); // [NOVO] Inicialização
        this.random = new Random();

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            // Tenta conectar à fila
            try {
                queue = (IQueue) registry.lookup("URLQueueInterface");
                System.out.println("[Gateway] URLQueue conectada com sucesso.");
            } catch (Exception e) {
                System.out.println("[Gateway] Aviso: URLQueue não encontrada no arranque.");
                throw e;
            }

            for (String name : registry.list()) {
                if (name.startsWith("Barrel")) {
                    try {
                        IBarrel barrel = (IBarrel) registry.lookup(name);
                        registerBarrel(barrel);
                    } catch (Exception e) {
                        System.out.println("[Gateway] Erro ao registar barrel encontrado: " + name);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[Gateway] Erro crítico no arranque: " + e.getMessage());
            throw new RemoteException("Erro de arranque", e);
        }
    }

    // --- Gestão de Subscrições (Callbacks) [NOVO] ---

    @Override
    public synchronized void subscribe(IClientCallback client) throws RemoteException {
        if (!subscribedClients.contains(client)) {
            subscribedClients.add(client);
            System.out.println("[Gateway] Novo cliente subscrito para atualizações em tempo real.");

            // Envia estatísticas imediatas ao conectar para não ficar vazio
            if (currentStats != null) {
                try {
                    client.onStatisticsUpdated(getSystemStats());
                } catch (RemoteException e) {
                    subscribedClients.remove(client);
                }
            }
        }
    }

    @Override
    public synchronized void unsubscribe(IClientCallback client) throws RemoteException {
        subscribedClients.remove(client);
        System.out.println("[Gateway] Cliente removeu subscrição.");
    }

    // Método privado para enviar dados a todos os subscritos
    private void notifyClients() {
        String statsOutput;
        try {
            statsOutput = getSystemStats(); // Gera o texto das estatísticas
        } catch (RemoteException e) {
            return;
        }

        List<IClientCallback> clientsToRemove = new ArrayList<>();
        List<IClientCallback> currentClients;

        // Copia a lista para iterar com segurança
        synchronized (this) {
            currentClients = new ArrayList<>(subscribedClients);
        }

        for (IClientCallback client : currentClients) {
            try {
                // "Empurra" a atualização para o cliente
                client.onStatisticsUpdated(statsOutput);
            } catch (RemoteException e) {
                // Se falhar (ex: cliente fechou), marca para remover
                clientsToRemove.add(client);
            }
        }

        // Remove clientes desconectados
        if (!clientsToRemove.isEmpty()) {
            synchronized (this) {
                subscribedClients.removeAll(clientsToRemove);
            }
            System.out.println("[Gateway] Removidos " + clientsToRemove.size() + " clientes inativos.");
        }
    }

    // --- Lógica Principal ---

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
                if (bestBarrel != null) return bestBarrel;
            }

            List<IBarrel> neverUsed = new ArrayList<>();
            for (Map.Entry<IBarrel, Long> entry : barrels.entrySet()) {
                if (entry.getValue() == 0) neverUsed.add(entry.getKey());
            }

            if (!neverUsed.isEmpty()) return neverUsed.get(random.nextInt(neverUsed.size()));
            return barrels.keySet().iterator().next();
        }
    }

    private boolean tryReconnect(String barrelName) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            IBarrel newBarrel = (IBarrel) registry.lookup(barrelName);
            registerBarrel(newBarrel);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String indexURL(String url) throws RemoteException {
        if (queue != null) {
            queue.addURL(url);
            return "URL '" + url + "' enviado para indexação.";
        }
        return "Erro: Queue não disponível.";
    }

    @Override
    public Map<String, UrlMetadata> search(List<String> terms) throws RemoteException {
        synchronized (barrels) {
            while (!barrels.isEmpty()) {
                IBarrel chosen = chooseBarrel();
                if (chosen == null) return Map.of();

                try {
                    barrels.put(chosen, System.currentTimeMillis());
                    String barrelName = extractBarrelName(chosen);

                    long start = System.currentTimeMillis();

                    Map<String, UrlMetadata> result = RetryLogic.executeWithRetry(
                            3, 2000,
                            () -> tryReconnect(barrelName),
                            () -> chosen.search(terms)
                    );

                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed == 0) elapsed = 1;

                    updateInternalStats(chosen, terms, Collections.emptyList(), elapsed);
                    updateSystemStatistics(); // Isto agora dispara o notifyClients()

                    return sortByIncomingLinks(result, chosen);

                } catch (RemoteException e) {
                    handleBarrelFailure(chosen, e, "search");
                }
            }
        }
        return Map.of();
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
                            3, 2000,
                            () -> tryReconnect(barrelName),
                            () -> chosen.getIncomingLinks(url)
                    );

                    List<String> links = new ArrayList<>(rawLinks);
                    long elapsed = System.currentTimeMillis() - start;

                    updateInternalStats(chosen, Collections.emptyList(), List.of(url), elapsed);
                    updateSystemStatistics(); // Dispara notificação

                    links.sort((a, b) -> Integer.compare(b.length(), a.length()));
                    return links;

                } catch (RemoteException e) {
                    handleBarrelFailure(chosen, e, "incomingLinks");
                }
            }
        }
        return List.of("Nenhum Barrel ativo disponível");
    }

    private void handleBarrelFailure(IBarrel chosen, RemoteException e, String context) throws RemoteException {
        if (isConnectionRefused(e)) {
            System.out.println("[Gateway] Barrel removido durante " + context + ": " + extractBarrelName(chosen));

            barrels.remove(chosen);
            responseTimes.remove(chosen);
            barrelInvertedSizes.remove(chosen);
            barrelIncomingSizes.remove(chosen);

            updateSystemStatistics(); // Dispara notificação para atualizar a lista de barrels ativos nos clientes
        } else {
            throw e;
        }
    }

    // --- Estatísticas ---

    @Override
    public String getSystemStats() throws RemoteException {
        if (currentStats == null) updateSystemStatistics();

        StringBuilder sb = new StringBuilder();
        sb.append("===== Estatísticas do Sistema (Tempo Real) =====\n\n");

        sb.append("=== Top 10 Termos ===\n");
        printTop10(currentStats.getTopSearchTerms(), sb);

        sb.append("\n=== Top 10 URLs ===\n");
        printTop10(currentStats.getTopConsultedUrls(), sb);

        sb.append("\n=== Barrels Ativos ===\n");
        List<BarrelStats> details = currentStats.getBarrelDetails();

        if (details.isEmpty()) sb.append("(Nenhum barrel ativo)\n");
        else details.forEach(bs -> sb.append(bs.toString()).append("\n"));

        return sb.toString();
    }

    @Override
    public void updateBarrelIndexSize(IBarrel barrel, int invertedSize, int incomingSize) throws RemoteException {
        synchronized (barrels) {
            barrelInvertedSizes.put(barrel, invertedSize);
            barrelIncomingSizes.put(barrel, incomingSize);
            updateSystemStatistics(); // Dispara notificação
        }
    }

    private void updateSystemStatistics() {
        synchronized (barrels) {
            List<BarrelStats> barrelStatsList = new ArrayList<>();

            for (IBarrel barrel : barrels.keySet()) {
                String name = extractBarrelName(barrel);

                List<Long> times = responseTimes.get(barrel);
                double avgTime = 0.0;
                int count = 0;

                if (times != null && !times.isEmpty()) {
                    avgTime = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    count = times.size();
                }

                int invSize = barrelInvertedSizes.getOrDefault(barrel, 0);
                int incSize = barrelIncomingSizes.getOrDefault(barrel, 0);

                barrelStatsList.add(new BarrelStats(name, "Active", avgTime, count, invSize, incSize));
            }

            this.currentStats = new SystemStatistics(
                    new HashMap<>(termFrequency),
                    new HashMap<>(urlFrequency),
                    barrelStatsList
            );
        }

        // [NOVO] Notificar clientes sempre que as estatísticas mudam
        notifyClients();
    }

    // --- Métodos Auxiliares ---

    private void updateInternalStats(IBarrel barrel, List<String> terms, List<String> urls, long elapsed) {
        for (String t : terms) termFrequency.put(t, termFrequency.getOrDefault(t, 0) + 1);
        for (String u : urls) urlFrequency.put(u, urlFrequency.getOrDefault(u, 0) + 1);

        // Com o fix no search (elapsed=1 se for 0), isto funciona sempre
        if (elapsed > 0) responseTimes.computeIfAbsent(barrel, k -> new ArrayList<>()).add(elapsed);
    }

    @Override
    public synchronized void registerBarrel(IBarrel barrel) throws RemoteException {
        if (!barrels.containsKey(barrel)) {
            barrels.put(barrel, 0L);
            responseTimes.put(barrel, new ArrayList<>());
            barrelInvertedSizes.put(barrel, 0);
            barrelIncomingSizes.put(barrel, 0);

            System.out.println("[Gateway] Novo Barrel registado: " + extractBarrelName(barrel));
            updateSystemStatistics(); // Dispara notificação
        }
    }

    private Map<String, UrlMetadata> sortByIncomingLinks(Map<String, UrlMetadata> unsortedResults, IBarrel barrel) {
        List<Map.Entry<String, UrlMetadata>> list = new ArrayList<>(unsortedResults.entrySet());
        list.sort((e1, e2) -> {
            try {
                int c1 = barrel.getIncomingLinks(e1.getKey()).size();
                int c2 = barrel.getIncomingLinks(e2.getKey()).size();
                return Integer.compare(c2, c1);
            } catch (Exception e) { return 0; }
        });
        Map<String, UrlMetadata> sorted = new LinkedHashMap<>();
        list.forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    private void printTop10(Map<String, Integer> map, StringBuilder sb) {
        if (map == null || map.isEmpty()) { sb.append("Sem dados.\n"); return; }
        map.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sb.append(String.format(" - %s: %d\n", e.getKey(), e.getValue())));
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
        try { return barrel.getName(); } catch (Exception e) { return "Barrel (Erro)"; }
    }

    public static void main(String[] args) {
        try {
            Gateway gateway = new Gateway();
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            registry.rebind("Gateway", gateway);
            System.out.println("[Gateway] Gateway pronta.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}