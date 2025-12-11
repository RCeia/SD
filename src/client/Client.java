package client;

import common.IClientCallback; // [NOVO] Importar a interface
import common.RetryLogic;
import common.UrlMetadata;
import gateway.IGateway;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject; // [NOVO] Necessário para o callback
import java.util.*;
import java.util.regex.Pattern;

public class Client {

    private static final int RETRY_LIMIT = 3;
    private static final long RETRY_DELAY = 1000;
    private static final String[] GATEWAY_HOSTS = {"localhost"};
    private static IGateway gateway = null;

    // Armazena últimos resultados
    private static List<String> lastSearchResults = new ArrayList<>();
    private static Map<String, UrlMetadata> lastSearchDescriptions = new HashMap<>();
    private static List<String> lastIncomingLinks = new ArrayList<>();

    // -------------------------------------------------------------------------
    // [NOVO] Implementação do Callback para Tempo Real
    // -------------------------------------------------------------------------
    private static class ClientCallbackImpl extends UnicastRemoteObject implements IClientCallback {

        protected ClientCallbackImpl() throws RemoteException {
            super();
        }

        @Override
        public void onStatisticsUpdated(String statsOutput) throws RemoteException {
            // Esta função é chamada automaticamente pela Gateway
            System.out.println("\n\n================================================");
            System.out.println(">>> ATUALIZAÇÃO RECEBIDA (TEMPO REAL) <<<");
            System.out.println("================================================");
            System.out.println(statsOutput);
            System.out.println("------------------------------------------------");
            System.out.println("Digite 'sair' para voltar ao menu.");
            System.out.print("> "); // Volta a mostrar o prompt
        }
    }

    // -------------------------------------------------------------------------
    // Conexão
    // -------------------------------------------------------------------------
    public static boolean connectToGateway() {
        for (int attempt = 0; attempt < RETRY_LIMIT; attempt++) {
            for (String host : GATEWAY_HOSTS) {
                try {
                    Registry registry = LocateRegistry.getRegistry(host, 1099);
                    gateway = (IGateway) registry.lookup("Gateway");
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
                    // Tentar sair graciosamente
                    System.exit(0);
                }
                default -> System.out.println("⚠ Opção inválida.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Funcionalidades Principais
    // -------------------------------------------------------------------------

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

    public static void searchPages(String searchTerm) {
        try {
            if (searchTerm.isBlank()) return;
            List<String> terms = Arrays.asList(searchTerm.trim().split("\\s+"));

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

    // [ALTERADO] Dashboard em Tempo Real
    public static void getSystemStats(Scanner scanner) {
        System.out.println("\n--- MODO DASHBOARD (TEMPO REAL) ---");
        System.out.println("A conectar ao fluxo de dados...");

        IClientCallback callback = null;

        try {
            // 1. Criar o objeto de callback
            callback = new ClientCallbackImpl();
            final IClientCallback myCallback = callback; // Variável final para usar no lambda

            // 2. Subscrever na Gateway
            RetryLogic.executeWithRetry(
                    RETRY_LIMIT, RETRY_DELAY,
                    Client::reconnectToGateway,
                    () -> { gateway.subscribe(myCallback); return null; }
            );

            System.out.println(">> Subscrito! Aguarde atualizações ou digite 'sair' para voltar.");

            // 3. Loop de espera (bloqueia o menu, mas recebe dados via callback)
            while (true) {
                String input = scanner.nextLine().trim();
                if (input.equalsIgnoreCase("sair") || input.equalsIgnoreCase("exit")) {
                    break;
                }
            }

            // 4. Cancelar subscrição ao sair
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
    // Auxiliares de Exibição
    // -------------------------------------------------------------------------

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

    private static boolean isValidURL(String url) {
        String regex = "^(https?|ftp)://[^\\s/$.?#].\\S*$";
        return Pattern.compile(regex).matcher(url).matches();
    }

    public static void main(String[] args) {
        if (connectToGateway()) {
            showMenu();
        } else {
            System.err.println("ERRO CRÍTICO: Não foi possível conectar a nenhuma Gateway.");
        }
    }
}