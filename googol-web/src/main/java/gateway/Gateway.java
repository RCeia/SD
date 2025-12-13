package gateway;

import common.UrlMetadata;
import queue.IQueue;
import barrel.IBarrel;
import common.RetryLogic;
import common.SystemStatistics; // Classe de dados
import common.BarrelStats;      // Classe de dados
import common.IClientCallback;  // Interface de callback atualizada

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.ConnectException;

/**
 * Implementação do Gateway de pesquisa (RMI).
 * <p>
 * O Gateway atua como o ponto central de coordenação do sistema. As suas principais responsabilidades incluem:
 * <ul>
 * <li>Servir como ponto de entrada para clientes e administradores.</li>
 * <li>Balanceamento de carga entre os Barrels disponíveis (baseado em tempos de resposta).</li>
 * <li>Tolerância a falhas (reconexão e retry logic).</li>
 * <li>Agregação de estatísticas do sistema e notificação em tempo real via Callbacks.</li>
 * <li>Monitorização da "saúde" dos Barrels (Heartbeat).</li>
 * </ul>
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 2.0
 */
public class Gateway extends UnicastRemoteObject implements IGateway {

    private final IQueue queue;
    private final Map<IBarrel, Long> barrels;
    private final Map<IBarrel, List<Long>> responseTimes;
    private final Map<String, Integer> termFrequency;
    private final Map<String, Integer> urlFrequency;
    private final Random random;

    // Estatísticas (Agora guardamos o objeto em vez de texto)
    private SystemStatistics currentStats;

    // Mapas auxiliares para guardar tamanhos reportados pelos Barrels
    private final Map<IBarrel, Integer> barrelInvertedSizes;
    private final Map<IBarrel, Integer> barrelIncomingSizes;

    // Lista de clientes RMI (Spring Boot) subscritos
    private final List<IClientCallback> subscribedClients;

    /**
     * Construtor do Gateway.
     * <p>
     * Inicializa as estruturas de dados, conecta-se ao RMI Registry local,
     * localiza a fila de URLs (URLQueue) e descobre automaticamente quaisquer
     * Barrels já registados no sistema. Inicia também a thread de monitorização (Heartbeat).
     * </p>
     *
     * @throws RemoteException Se ocorrer um erro crítico no arranque do servidor RMI.
     */
    public Gateway() throws RemoteException {
        super();
        this.barrels = new HashMap<>();
        this.responseTimes = new HashMap<>();
        this.termFrequency = new HashMap<>();
        this.urlFrequency = new HashMap<>();
        this.barrelInvertedSizes = new HashMap<>();
        this.barrelIncomingSizes = new HashMap<>();
        this.subscribedClients = new ArrayList<>();
        this.random = new Random();

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);

            // Tentar conectar à Queue
            try {
                queue = (IQueue) registry.lookup("URLQueueInterface");
                System.out.println("[Gateway] URLQueue conectada com sucesso.");
            } catch (Exception e) {
                System.out.println("[Gateway] Aviso: URLQueue não encontrada no arranque.");
                throw e; // Relança para o main apanhar ou lida conforme a tua lógica
            }

            // Descobrir Barrels já registados
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

        startHeartbeatMonitor();
    }

    // --- GESTÃO DE SUBSCRIÇÕES (ATUALIZADO PARA OBJETOS) ---

    /**
     * Subscreve um cliente para receber atualizações de estatísticas em tempo real.
     * <p>
     * Se existirem estatísticas atuais disponíveis, estas são enviadas imediatamente
     * ao cliente para popular o dashboard inicial.
     * </p>
     *
     * @param client A referência para a interface de callback do cliente.
     * @throws RemoteException Se falhar a comunicação com o cliente.
     */
    @Override
    public synchronized void subscribe(IClientCallback client) throws RemoteException {
        if (!subscribedClients.contains(client)) {
            subscribedClients.add(client);
            System.out.println("[Gateway] Novo cliente subscrito.");

            // Se já tivermos estatísticas calculadas, enviamos imediatamente
            // para o dashboard do cliente não começar vazio.
            if (currentStats != null) {
                try {
                    client.onStatisticsUpdated(currentStats);
                } catch (RemoteException e) {
                    System.err.println("[Gateway] Falha ao enviar stats iniciais. Cliente removido.");
                    subscribedClients.remove(client);
                }
            }
        }
    }

    /**
     * Remove a subscrição de um cliente, parando o envio de atualizações.
     *
     * @param client A referência para a interface de callback do cliente a remover.
     * @throws RemoteException Se ocorrer um erro RMI.
     */
    @Override
    public synchronized void unsubscribe(IClientCallback client) throws RemoteException {
        subscribedClients.remove(client);
        System.out.println("[Gateway] Cliente removeu subscrição.");
    }

    /**
     * Inicia uma thread em background para monitorizar a saúde dos Barrels (Heartbeat).
     * <p>
     * Verifica periodicamente se os Barrels registados respondem. Se um Barrel não responder,
     * é considerado "morto" e removido das listas de balanceamento e estatísticas.
     * </p>
     */
    private void startHeartbeatMonitor() {
        new Thread(() -> {
            while (true) {
                try {
                    // Verifica a cada 3 segundos
                    Thread.sleep(3000);

                    List<IBarrel> deadBarrels = new ArrayList<>();

                    synchronized (barrels) {
                        // 1. Testar conexão com cada Barrel
                        for (IBarrel barrel : barrels.keySet()) {
                            try {
                                // Tenta uma chamada leve RMI
                                barrel.isActive();
                            } catch (RemoteException e) {
                                // Se der exceção, o Barrel está morto/inacessível
                                deadBarrels.add(barrel);
                            }
                        }

                        // 2. Remover os mortos
                        if (!deadBarrels.isEmpty()) {
                            for (IBarrel dead : deadBarrels) {
                                System.out.println("[Gateway] Heartbeat falhou. Removendo Barrel morto.");
                                barrels.remove(dead);
                                responseTimes.remove(dead);
                                barrelInvertedSizes.remove(dead);
                                barrelIncomingSizes.remove(dead);
                            }

                            // 3. Forçar atualização imediata do Dashboard
                            updateSystemStatistics();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[Gateway] Erro no monitor de Heartbeat: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Notifica todos os clientes subscritos com as estatísticas mais recentes do sistema.
     * Remove automaticamente clientes que não estejam acessíveis.
     */
    private void notifyClients() {
        // Se ainda não há dados, não vale a pena notificar
        if (currentStats == null) return;

        List<IClientCallback> clientsToRemove = new ArrayList<>();
        List<IClientCallback> currentClients;

        // Cópia defensiva para evitar ConcurrentModificationException
        synchronized (this) {
            currentClients = new ArrayList<>(subscribedClients);
        }

        for (IClientCallback client : currentClients) {
            try {
                // ENVIO DO OBJETO COMPLETO
                client.onStatisticsUpdated(currentStats);
            } catch (RemoteException e) {
                clientsToRemove.add(client);
            }
        }

        if (!clientsToRemove.isEmpty()) {
            synchronized (this) {
                subscribedClients.removeAll(clientsToRemove);
            }
            System.out.println("[Gateway] Limpeza: " + clientsToRemove.size() + " clientes inativos removidos.");
        }
    }

    // --- LÓGICA PRINCIPAL (SEARCH / INDEX / LINKS) ---

    /**
     * Seleciona o melhor Barrel para processar um pedido.
     * <p>
     * A estratégia de seleção é:
     * 1. Barrel com menor tempo médio de resposta.
     * 2. Se não houver dados históricos suficiente, um Barrel nunca usado.
     * 3. Aleatório ou o primeiro disponível.
     * </p>
     *
     * @return A referência para o Barrel escolhido ou null se não houver nenhum disponível.
     */
    private IBarrel chooseBarrel() {
        synchronized (barrels) {
            if (barrels.isEmpty()) return null;

            // Estratégia: Escolher o melhor tempo médio de resposta
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

            // Fallback: Tentar usar barrels nunca usados
            List<IBarrel> neverUsed = new ArrayList<>();
            for (Map.Entry<IBarrel, Long> entry : barrels.entrySet()) {
                if (entry.getValue() == 0) neverUsed.add(entry.getKey());
            }

            if (!neverUsed.isEmpty()) return neverUsed.get(random.nextInt(neverUsed.size()));

            // Último recurso: qualquer um serve
            return barrels.keySet().iterator().next();
        }
    }

    /**
     * Tenta restabelecer a ligação com um Barrel através do seu nome no RMI Registry.
     *
     * @param barrelName O nome do Barrel a reconectar.
     * @return true se a reconexão for bem sucedida, false caso contrário.
     */
    private boolean tryReconnect(String barrelName) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            IBarrel newBarrel = (IBarrel) registry.lookup(barrelName);
            registerBarrel(newBarrel); // Re-regista e atualiza stats
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Envia um URL para a fila de indexação.
     *
     * @param url O URL a ser indexado.
     * @return Mensagem de estado sobre a operação.
     * @throws RemoteException Se ocorrer erro na comunicação RMI.
     */
    @Override
    public String indexURL(String url) throws RemoteException {
        if (queue != null) {
            queue.addURL(url);
            return "URL enviado para indexação.";
        }
        return "Erro: Queue indisponível.";
    }

    /**
     * Realiza uma pesquisa nos Barrels disponíveis utilizando lógica de tentativas (Retry).
     * <p>
     * Seleciona um Barrel, executa a pesquisa e mede o tempo de resposta.
     * Em caso de falha, tenta reconectar ou seleciona outro Barrel.
     * </p>
     *
     * @param terms Lista de termos a pesquisar.
     * @return Mapa de URLs e metadados encontrados.
     * @throws RemoteException Se todos os Barrels falharem.
     */
    @Override
    public Map<String, UrlMetadata> search(List<String> terms) throws RemoteException {
        synchronized (barrels) {
            while (!barrels.isEmpty()) {
                IBarrel chosen = chooseBarrel();
                if (chosen == null) break;

                try {
                    barrels.put(chosen, System.currentTimeMillis());
                    String barrelName = extractBarrelName(chosen);

                    long start = System.currentTimeMillis();

                    // Tenta executar com repetições (Retry Logic)
                    // O Barrel agora já faz a ordenação e a paginação internamente!
                    Map<String, UrlMetadata> result = RetryLogic.executeWithRetry(
                            3, 2000,
                            () -> tryReconnect(barrelName),
                            () -> chosen.search(terms)
                    );

                    long elapsed = Math.max(1, System.currentTimeMillis() - start);

                    // Atualiza estatísticas internas e notifica clientes
                    updateInternalStats(chosen, terms, Collections.emptyList(), elapsed);
                    updateSystemStatistics();

                    // ALTERAÇÃO: Retorna direto. Não ordenamos mais aqui para evitar lentidão.
                    return result;

                } catch (RemoteException e) {
                    handleBarrelFailure(chosen, e, "search");
                }
            }
        }
        return new HashMap<>(); // Retorna vazio se falhar tudo
    }

    /**
     * Obtém os links que apontam para um determinado URL (Incoming Links).
     *
     * @param url O URL de destino.
     * @return Lista de URLs que apontam para o destino.
     * @throws RemoteException Se ocorrer erro na comunicação RMI.
     */
    @Override
    public List<String> getIncomingLinks(String url) throws RemoteException {
        synchronized (barrels) {
            while (!barrels.isEmpty()) {
                IBarrel chosen = chooseBarrel();
                if (chosen == null) break;

                try {
                    barrels.put(chosen, System.currentTimeMillis());
                    String barrelName = extractBarrelName(chosen);
                    long start = System.currentTimeMillis();

                    Collection<String> rawLinks = RetryLogic.executeWithRetry(
                            3, 2000,
                            () -> tryReconnect(barrelName),
                            () -> chosen.getIncomingLinks(url)
                    );

                    long elapsed = Math.max(1, System.currentTimeMillis() - start);

                    List<String> links = new ArrayList<>(rawLinks);

                    // Atualiza stats e notifica
                    updateInternalStats(chosen, Collections.emptyList(), List.of(url), elapsed);
                    updateSystemStatistics();

                    // Ordena por tamanho (exemplo simples)
                    links.sort((a, b) -> Integer.compare(b.length(), a.length()));
                    return links;

                } catch (RemoteException e) {
                    handleBarrelFailure(chosen, e, "getIncomingLinks");
                }
            }
        }
        return new ArrayList<>();
    }

    /**
     * Trata falhas de comunicação com Barrels.
     * Se a falha for de conexão (recusada), remove o Barrel do sistema.
     *
     * @param chosen O Barrel que falhou.
     * @param e A exceção capturada.
     * @param context O nome do método onde ocorreu a falha.
     * @throws RemoteException Se a exceção não for de conexão (relança).
     */
    private void handleBarrelFailure(IBarrel chosen, RemoteException e, String context) throws RemoteException {
        if (isConnectionRefused(e)) {
            System.out.println("[Gateway] Barrel removido durante " + context + ": " + extractBarrelName(chosen));

            // Remove de todos os mapas
            barrels.remove(chosen);
            responseTimes.remove(chosen);
            barrelInvertedSizes.remove(chosen);
            barrelIncomingSizes.remove(chosen);

            // Atualiza stats para refletir a remoção do Barrel
            updateSystemStatistics();
        } else {
            throw e; // Se não for falha de conexão, relança
        }
    }

    // --- ESTATÍSTICAS DO SISTEMA (CONSTRUÇÃO DO OBJETO) ---

    /**
     * Recebe e atualiza os tamanhos dos índices reportados por um Barrel.
     *
     * @param barrel O Barrel que reporta.
     * @param invertedSize Tamanho do índice invertido.
     * @param incomingSize Tamanho do índice de links.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public void updateBarrelIndexSize(IBarrel barrel, int invertedSize, int incomingSize) throws RemoteException {
        synchronized (barrels) {
            barrelInvertedSizes.put(barrel, invertedSize);
            barrelIncomingSizes.put(barrel, incomingSize);
            updateSystemStatistics(); // Recalcula e notifica sempre que há dados novos
        }
    }

    /**
     * Recalcula as estatísticas globais do sistema e notifica os clientes.
     * Cria um snapshot (`SystemStatistics`) contendo frequência de termos, URLs
     * e estado de cada Barrel.
     */
    private void updateSystemStatistics() {
        synchronized (barrels) {
            List<BarrelStats> barrelStatsList = new ArrayList<>();

            for (IBarrel barrel : barrels.keySet()) {
                String name = extractBarrelName(barrel);

                // Calcular tempo médio
                List<Long> times = responseTimes.get(barrel);
                double avgTime = 0.0;
                int count = 0;
                if (times != null && !times.isEmpty()) {
                    avgTime = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    count = times.size();
                }

                // Obter tamanhos reportados
                int invSize = barrelInvertedSizes.getOrDefault(barrel, 0);
                int incSize = barrelIncomingSizes.getOrDefault(barrel, 0);

                // --- CORREÇÃO AQUI ---
                // Mude 'true' para "Active" (ou a string que preferir)
                barrelStatsList.add(new BarrelStats(name, "Active", avgTime, count, invSize, incSize));

                // NOTA: Se o 3º argumento (avgTime) der erro também, verifique se o construtor
                // pede 'double' ou 'int'. Se pedir int, use: (int) avgTime
            }

            // Criar o objeto principal
            this.currentStats = new SystemStatistics(
                    new HashMap<>(termFrequency),
                    new HashMap<>(urlFrequency),
                    barrelStatsList
            );
        }

        // Enviar para todos os clientes ligados
        notifyClients();
    }

    /**
     * Atualiza os contadores internos de frequência de pesquisa e tempos de resposta.
     *
     * @param barrel O Barrel que respondeu.
     * @param terms Termos pesquisados (para estatística).
     * @param urls URLs envolvidos (para estatística).
     * @param elapsed Tempo decorrido na operação.
     */
    private void updateInternalStats(IBarrel barrel, List<String> terms, List<String> urls, long elapsed) {
        for (String t : terms) {
            // LÓGICA NOVA: Remove a tag [PAGE:X] antes de contar para a estatística
            String termoLimpo = t;
            if (t.contains("[PAGE:")) {
                try {
                    termoLimpo = t.substring(0, t.lastIndexOf("[PAGE:"));
                } catch (Exception ignored) {}
            }

            // Só conta se a palavra não for vazia
            if (!termoLimpo.isEmpty()) {
                termFrequency.put(termoLimpo, termFrequency.getOrDefault(termoLimpo, 0) + 1);
            }
        }

        for (String u : urls) {
            urlFrequency.put(u, urlFrequency.getOrDefault(u, 0) + 1);
        }

        if (elapsed > 0) {
            responseTimes.computeIfAbsent(barrel, k -> new ArrayList<>()).add(elapsed);
        }
    }

    // --- MÉTODOS AUXILIARES ---

    /**
     * Regista um novo Barrel no Gateway.
     *
     * @param barrel A referência para o Barrel.
     * @throws RemoteException Se ocorrer erro RMI.
     */
    @Override
    public synchronized void registerBarrel(IBarrel barrel) throws RemoteException {
        if (!barrels.containsKey(barrel)) {
            barrels.put(barrel, 0L);
            responseTimes.put(barrel, new ArrayList<>());
            barrelInvertedSizes.put(barrel, 0);
            barrelIncomingSizes.put(barrel, 0);

            System.out.println("[Gateway] Barrel registado: " + extractBarrelName(barrel));
            updateSystemStatistics(); // Notifica nova entrada
        }
    }

    /**
     * Ordena resultados baseando-se no número de incoming links.
     *
     * @param unsortedResults O mapa de resultados não ordenados.
     * @param barrel O barrel para consultar contagens de links.
     * @return Mapa ordenado.
     */
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

    /**
     * Verifica se uma exceção RMI foi causada por recusa de conexão.
     *
     * @param e A exceção RemoteException.
     * @return true se a causa raiz for ConnectException.
     */
    private boolean isConnectionRefused(RemoteException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConnectException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Tenta extrair o nome do Barrel (método getName) de forma segura.
     *
     * @param barrel O objeto Barrel.
     * @return O nome do Barrel ou "Barrel (N/A)" em caso de erro.
     */
    private String extractBarrelName(IBarrel barrel) {
        try { return barrel.getName(); } catch (Exception e) { return "Barrel (N/A)"; }
    }

    /**
     * Método principal (Main) para iniciar o serviço Gateway.
     *
     * @param args Argumentos de linha de comando.
     */
    public static void main(String[] args) {
        try {
            // ALTERAÇÃO: Força o uso do localhost para evitar erros de rede (192.168.x.x)
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");

            Gateway gateway = new Gateway();
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            registry.rebind("Gateway", gateway);
            System.out.println("[Gateway] Serviço Gateway RMI pronto.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}