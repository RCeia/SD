package client;

import common.IClientCallback;
import common.RetryLogic;
import common.UrlMetadata;
import common.SystemStatistics; // [NOVO] Importar a classe de dados
import common.BarrelStats;      // [NOVO] Importar a classe de dados
import gateway.IGateway;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Cliente RMI (Consola) para o motor de busca Googol.
 * <p>
 * Esta classe fornece uma interface de linha de comandos (CLI) que permite aos utilizadores:
 * <ul>
 * <li>Enviar URLs para indexação.</li>
 * <li>Pesquisar páginas (com filtragem de Stop Words).</li>
 * <li>Consultar links de entrada (incoming links).</li>
 * <li>Visualizar um dashboard em tempo real com estatísticas do sistema.</li>
 * </ul>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class Client {

    /**
     * Limite de tentativas de reconexão em caso de falha de comunicação.
     */
    private static final int RETRY_LIMIT = 3;

    /**
     * Tempo de espera (em ms) entre tentativas de reconexão.
     */
    private static final long RETRY_DELAY = 1000;

    /**
     * Lista de hosts onde o Gateway pode estar a correr (atualmente apenas localhost).
     */
    private static final String[] GATEWAY_HOSTS = {"localhost"};

    /**
     * Referência remota para o serviço Gateway.
     */
    private static IGateway gateway = null;

    /**
     * Cache local dos últimos resultados de pesquisa (URLs) para paginação.
     */
    private static List<String> lastSearchResults = new ArrayList<>();

    /**
     * Cache local dos metadados (títulos/citações) da última pesquisa.
     */
    private static Map<String, UrlMetadata> lastSearchDescriptions = new HashMap<>();

    /**
     * Cache local dos últimos links de entrada consultados para paginação.
     */
    private static List<String> lastIncomingLinks = new ArrayList<>();

    // [SERVIÇO DE STOP WORDS]
    /**
     * Referência remota para o serviço de identificação de Stop Words.
     */
    private static adaptivestopwords.IAdaptiveStopWords stopWordsService = null;

    // -------------------------------------------------------------------------
    // [ATUALIZADO] Callback agora recebe o Objeto SystemStatistics
    // -------------------------------------------------------------------------

    /**
     * Implementação interna da interface de callback para receber atualizações do Dashboard.
     * <p>
     * Esta classe é exportada como objeto remoto para permitir que o Gateway envie
     * dados estatísticos (push) para o Cliente.
     * </p>
     */
    private static class ClientCallbackImpl extends UnicastRemoteObject implements IClientCallback {

        /**
         * Construtor do callback.
         *
         * @throws RemoteException Se ocorrer erro na exportação do objeto.
         */
        protected ClientCallbackImpl() throws RemoteException {
            super();
        }

        /**
         * Método invocado remotamente pelo Gateway quando há novas estatísticas.
         * <p>
         * Exibe na consola o Top 10 de pesquisas, Top 10 de URLs e o estado de carga dos Barrels.
         * </p>
         *
         * @param stats Objeto {@code SystemStatistics} contendo os dados atualizados.
         * @throws RemoteException Se ocorrer erro na comunicação.
         */
        @Override
        public void onStatisticsUpdated(SystemStatistics stats) throws RemoteException {
            System.out.println("\n\n");
            System.out.println("================================================");
            System.out.println("       GOOGOL DASHBOARD (TEMPO REAL)           ");
            System.out.println("================================================");

            if (stats == null) {
                System.out.println("A aguardar dados...");
                return;
            }

            // 1. Mostrar Top Pesquisas
            System.out.println("\n--- TOP 10 PESQUISAS ---");
            printTopMap(stats.getTopSearchTerms());

            // 2. Mostrar Top URLs
            System.out.println("\n--- TOP 10 URLs CONSULTADOS ---");
            printTopMap(stats.getTopConsultedUrls());

            // 3. Mostrar Estado dos Barrels
            System.out.println("\n--- ESTADO DOS BARRELS ---");
            List<BarrelStats> barrels = stats.getBarrelDetails();

            if (barrels == null || barrels.isEmpty()) {
                System.out.println(" (Nenhum Barrel ativo)");
            } else {
                for (BarrelStats b : barrels) {
                    System.out.printf("Barrel: %-15s | Status: %s%n", b.getName(), b.getStatus());

                    // --- CORREÇÃO AQUI: Usar os getters corretos do BarrelStats.java ---
                    System.out.printf("   Palavras: %-6d | Links: %-6d | Tempo Médio: %.2fms (%d reqs)%n",
                            b.getInvertedIndexCount(),   // Era getWordCount()
                            b.getIncomingLinksCount(),   // Era getLinkCount()
                            b.getAvgResponseTime(),
                            b.getRequestCount());

                    System.out.println("   -----------------------");
                }
            }

            System.out.println("================================================");
            System.out.println("Digite 'sair' para voltar ao menu.");
            System.out.print("> ");
        }

        /**
         * Método auxiliar para ordenar e imprimir o Top 10 de um mapa de frequências.
         *
         * @param map Mapa contendo Item -> Frequência.
         */
        private void printTopMap(Map<String, Integer> map) {
            if (map == null || map.isEmpty()) {
                System.out.println(" (Sem dados)");
                return;
            }

            map.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) // Ordem decrescente
                    .limit(10) // Apenas Top 10
                    .forEach(e -> System.out.printf(" %-20s : %d%n", e.getKey(), e.getValue()));
        }
    }

    // -------------------------------------------------------------------------
    // Conexão
    // -------------------------------------------------------------------------

    /**
     * Estabelece a conexão inicial com o Gateway e o serviço de Stop Words via RMI.
     * Tenta conectar-se aos hosts definidos em {@code GATEWAY_HOSTS} com retentativas.
     *
     * @return true se a conexão for bem-sucedida, false caso contrário.
     */
    public static boolean connectToGateway() {
        for (int attempt = 0; attempt < RETRY_LIMIT; attempt++) {
            for (String host : GATEWAY_HOSTS) {
                try {
                    Registry registry = LocateRegistry.getRegistry(host, 1099);

                    gateway = (IGateway) registry.lookup("Gateway");

                    stopWordsService = (adaptivestopwords.IAdaptiveStopWords) registry.lookup("AdaptiveStopWords");

                    System.out.println("Conectado com sucesso à Gateway em " + host);
                    return true;
                } catch (RemoteException e) {
                    System.err.println("Falha ao conectar à Gateway em " + host + ": " + e.getMessage());
                } catch (NotBoundException e) {
                    // Gateway ainda não está pronta
                }

                try { Thread.sleep(RETRY_DELAY); } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }

    /**
     * Tenta reconectar ao Gateway. Método auxiliar utilizado na lógica de Retry.
     *
     * @return true se a reconexão for bem-sucedida.
     */
    public static boolean reconnectToGateway() {
        System.out.println("A tentar reconectar à Gateway...");
        boolean success = connectToGateway();
        if (success) System.out.println("Reconexão bem-sucedida!");
        else System.err.println("Falha ao reconectar.");
        return success;
    }

    // -------------------------------------------------------------------------
    // Menus
    // -------------------------------------------------------------------------

    /**
     * Exibe o menu principal e processa as escolhas do utilizador.
     * Mantém o loop da aplicação até que o utilizador escolha sair.
     */
    public static void showMenu() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n===== GOOGOL CLIENT =====");
            System.out.println("1 - Indexar URL");
            System.out.println("2 - Pesquisar páginas");
            System.out.println("3 - Consultar links para uma página");
            System.out.println("4 - Dashboard do Sistema (Tempo Real)");
            System.out.println("5 - Sair");
            System.out.print("> ");

            int choice;
            try {
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida.");
                continue;
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Digite o URL: ");
                    indexURL(scanner.nextLine());
                }
                case 2 -> {
                    System.out.print("Pesquisa: ");
                    searchPages(scanner.nextLine());
                }
                case 3 -> {
                    System.out.print("URL Alvo: ");
                    getIncomingLinks(scanner.nextLine());
                }
                case 4 -> getSystemStats(scanner);
                case 5 -> {
                    System.out.println("A sair...");
                    System.exit(0);
                }
                default -> System.out.println("⚠ Opção inválida.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Funcionalidades Principais
    // -------------------------------------------------------------------------

    /**
     * Envia um URL para ser indexado pelo sistema.
     * Realiza validação de formato e utiliza retry logic.
     *
     * @param url O URL a indexar.
     */
    public static void indexURL(String url) {
        if (!isValidURL(url)) {
            System.err.println("URL inválido (deve começar por http:// ou https://)");
            return;
        }

        try {
            String result = RetryLogic.executeWithRetry(
                    RETRY_LIMIT, RETRY_DELAY,
                    Client::reconnectToGateway,
                    () -> gateway.indexURL(url)
            );
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("Erro ao indexar URL: " + e.getMessage());
        }
    }

    /**
     * Realiza a pesquisa de páginas com base numa string de termos.
     * <p>
     * O processo inclui:
     * 1. Divisão da string em termos.
     * 2. Consulta ao serviço de Stop Words para filtrar termos irrelevantes.
     * 3. Envio dos termos filtrados ao Gateway.
     * 4. Exibição paginada dos resultados.
     * </p>
     *
     * @param searchTerm A string de pesquisa inserida pelo utilizador.
     */
    public static void searchPages(String searchTerm) {
        try {
            if (searchTerm.isBlank()) return;
            // ArrayList de termos de pesquisa independentes para que possamos retirar as Stop Words
            List<String> terms = new ArrayList<>(Arrays.asList(searchTerm.trim().split("\\s+")));

            // [SERVIÇO DE STOP WORDS] - Bloquear a pesquisa de termos que sejam Stop Words
            if (stopWordsService != null) {
                try {
                    Set<String> stopWords = new HashSet<>(stopWordsService.getStopWords());

                    System.out.println(stopWords);

                    terms.removeIf(term -> stopWords.contains(term.toLowerCase()));

                    if (terms.isEmpty()) {
                        System.out.println("Nenhum resultado encontrado. (É Stop Word)");
                        return;
                    }
                } catch (RemoteException e) {
                    System.err.println("Aviso: Não foi possível aceder às stop words. Prosseguindo sem filtro.");
                }
            }

            if (gateway != null) {
                Map<String, UrlMetadata> results = RetryLogic.executeWithRetry(
                        RETRY_LIMIT, RETRY_DELAY,
                        Client::reconnectToGateway,
                        () -> gateway.search(terms)
                );

                if (results == null || results.isEmpty()) {
                    System.out.println("Nenhum resultado encontrado.");
                    return;
                }

                lastSearchResults = new ArrayList<>(results.keySet());
                lastSearchDescriptions = results;

                showPagedResults(lastSearchResults, lastSearchDescriptions, "PESQUISA");
            } else {
                System.err.println("Gateway não conectada.");
                reconnectToGateway();
            }
        } catch (Exception e) {
            System.err.println("Erro na pesquisa: " + e.getMessage());
        }
    }

    /**
     * Consulta e exibe os links que apontam para um determinado URL (Backlinks).
     *
     * @param url O URL alvo.
     */
    public static void getIncomingLinks(String url) {
        try {
            List<String> links = RetryLogic.executeWithRetry(
                    RETRY_LIMIT, RETRY_DELAY,
                    Client::reconnectToGateway,
                    () -> gateway.getIncomingLinks(url)
            );

            if (links == null || links.isEmpty()) {
                System.out.println("Nenhum link encontrado.");
                return;
            }

            lastIncomingLinks = links;
            showPagedResults(lastIncomingLinks, null, "LINKS DE ENTRADA");

        } catch (Exception e) {
            System.err.println("Erro ao consultar links: " + e.getMessage());
        }
    }

    // [DASHBOARD] - Lógica mantém-se, apenas o callback interno mudou

    /**
     * Inicia o modo Dashboard, subscrevendo o cliente para atualizações em tempo real.
     * <p>
     * Cria uma instância de {@code ClientCallbackImpl}, regista-a no Gateway e
     * bloqueia a execução até que o utilizador decida sair.
     * </p>
     *
     * @param scanner Scanner para leitura da entrada do utilizador (para sair).
     */
    public static void getSystemStats(Scanner scanner) {
        System.out.println("\n--- MODO DASHBOARD (TEMPO REAL) ---");
        System.out.println("A conectar ao fluxo de dados...");

        IClientCallback callback = null;

        try {
            callback = new ClientCallbackImpl();
            final IClientCallback myCallback = callback;

            RetryLogic.executeWithRetry(
                    RETRY_LIMIT, RETRY_DELAY,
                    Client::reconnectToGateway,
                    () -> { gateway.subscribe(myCallback); return null; }
            );

            System.out.println(">> Subscrito! Aguarde atualizações ou digite 'sair' para voltar.");

            while (true) {
                String input = scanner.nextLine().trim();
                if (input.equalsIgnoreCase("sair") || input.equalsIgnoreCase("exit")) {
                    break;
                }
            }

            gateway.unsubscribe(myCallback);
            System.out.println("Dashboard fechado.");

        } catch (Exception e) {
            System.err.println("Erro no Dashboard: " + e.getMessage());
            try {
                if (callback != null && gateway != null) gateway.unsubscribe(callback);
            } catch (RemoteException ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Auxiliares
    // -------------------------------------------------------------------------

    /**
     * Exibe resultados de pesquisa ou links de forma paginada.
     *
     * @param results Lista de URLs a exibir.
     * @param descriptions Mapa opcional com metadados (Título, Citação) para exibição rica.
     * @param type Tipo de listagem (para título da secção).
     */
    private static void showPagedResults(List<String> results, Map<String, UrlMetadata> descriptions, String type) {
        Scanner scanner = new Scanner(System.in);
        int total = results.size();
        int pageSize = 5;
        int currentPage = 0;

        while (true) {
            int start = currentPage * pageSize;
            int end = Math.min(start + pageSize, total);

            if (start >= total) {
                currentPage = 0;
                continue;
            }

            List<String> page = results.subList(start, end);

            System.out.println("\n=== RESULTADOS " + type + " (" + (start + 1) + "-" + end + " de " + total + ") ===");

            for (String url : page) {
                if (descriptions != null && descriptions.containsKey(url)) {
                    UrlMetadata meta = descriptions.get(url);
                    System.out.println("------------------------------------------------");
                    System.out.println("TITULO:  " + (meta.getTitle() != null ? meta.getTitle() : "Sem título"));
                    System.out.println("URL:     " + url);
                    System.out.println("CITAÇÃO: " + (meta.getCitation() != null ? meta.getCitation() : ""));
                } else {
                    System.out.println(" -> " + url);
                }
            }
            System.out.println("------------------------------------------------");

            System.out.println("1 - Próxima | 2 - Anterior | 3 - Voltar ao Menu");
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.equals("1")) {
                if (end < total) currentPage++;
                else System.out.println(">> Fim da lista.");
            } else if (input.equals("2")) {
                if (currentPage > 0) currentPage--;
                else System.out.println(">> Início da lista.");
            } else if (input.equals("3")) {
                break;
            }
        }
    }

    /**
     * Valida se uma string é um URL válido (começado por http, https ou ftp).
     *
     * @param url A string a validar.
     * @return true se o formato for válido, false caso contrário.
     */
    private static boolean isValidURL(String url) {
        String regex = "^(https?|ftp)://[^\\s/$.?#].\\S*$";
        return Pattern.compile(regex).matcher(url).matches();
    }

    /**
     * Ponto de entrada da aplicação Cliente.
     * Tenta conectar ao Gateway e, se bem-sucedido, exibe o menu.
     *
     * @param args Argumentos de linha de comando (não utilizados).
     */
    public static void main(String[] args) {
        if (connectToGateway()) {
            showMenu();
        } else {
            System.err.println("ERRO CRÍTICO: Não foi possível conectar a nenhuma Gateway.");
        }
    }
}