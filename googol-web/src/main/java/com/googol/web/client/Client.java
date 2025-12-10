package com.googol.web.client;

import com.googol.web.common.RetryLogic;
import com.googol.web.common.UrlMetadata;
import com.googol.web.gateway.IGateway;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.regex.Pattern;

public class Client {

    private static final int RETRY_LIMIT = 3;
    private static final long RETRY_DELAY = 1000;
    private static final String[] GATEWAY_HOSTS = {"localhost", "backupGateway"};
    private static IGateway gateway = null;

    // Armazena últimos resultados para navegação
    private static List<String> lastSearchResults = new ArrayList<>();

    // ALTERAÇÃO: Agora guardamos o objeto rico UrlMetadata
    private static Map<String, UrlMetadata> lastSearchDescriptions = new HashMap<>();

    private static List<String> lastIncomingLinks = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Conexão e Reconexão
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
                    System.err.println("Gateway não encontrada no host " + host);
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
    // Menus e Interação
    // -------------------------------------------------------------------------
    public static void showMenu() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n===== GOOGOL CLIENT =====");
            System.out.println("1 - Indexar URL");
            System.out.println("2 - Pesquisar páginas");
            System.out.println("3 - Consultar links para uma página");
            System.out.println("4 - Obter estatísticas do sistema");
            System.out.println("5 - Sair");
            System.out.print("> ");

            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
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
                case 4 -> getSystemStats();
                case 5 -> {
                    System.out.println("A sair...");
                    return;
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
            List<String> terms = Arrays.asList(searchTerm.trim().split("\\s+"));

            if (gateway != null) {
                // ALTERAÇÃO: Recebe Map<String, UrlMetadata>
                Map<String, UrlMetadata> results = gateway.search(terms);

                if (results == null || results.isEmpty()) {
                    System.out.println("Nenhum resultado encontrado.");
                    return;
                }

                lastSearchResults = new ArrayList<>(results.keySet());
                lastSearchDescriptions = results;

                showPagedResults(lastSearchResults, lastSearchDescriptions, "PESQUISA");
            } else {
                System.err.println("Gateway não conectada.");
            }
        } catch (RemoteException e) {
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
            // Passamos null nas descrições porque incoming links é só lista de URLs
            showPagedResults(lastIncomingLinks, null, "LINKS DE ENTRADA");

        } catch (Exception e) {
            System.err.println("Erro ao consultar links: " + e.getMessage());
        }
    }

    public static void getSystemStats() {
        try {
            String stats = RetryLogic.executeWithRetry(
                    RETRY_LIMIT, RETRY_DELAY,
                    Client::reconnectToGateway,
                    () -> gateway.getSystemStats()
            );
            System.out.println(stats);
        } catch (Exception e) {
            System.err.println("Erro ao obter estatísticas: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Auxiliares de Exibição
    // -------------------------------------------------------------------------

    private static void showPagedResults(List<String> results, Map<String, UrlMetadata> descriptions, String type) {
        Scanner scanner = new Scanner(System.in);
        int total = results.size();
        int pageSize = 5; // Reduzi para 5 para ser mais legível com metadados
        int currentPage = 0;

        while (true) {
            int start = currentPage * pageSize;
            int end = Math.min(start + pageSize, total);

            if (start >= total) { // Proteção caso a lista mude
                currentPage = 0;
                continue;
            }

            List<String> page = results.subList(start, end);

            System.out.println("\n=== RESULTADOS " + type + " (" + (start + 1) + "-" + end + " de " + total + ") ===");

            for (String url : page) {
                if (descriptions != null && descriptions.containsKey(url)) {
                    // ALTERAÇÃO: Exibição bonita usando UrlMetadata
                    UrlMetadata meta = descriptions.get(url);
                    System.out.println("------------------------------------------------");
                    System.out.println("Titulo: " + meta.getTitle());
                    System.out.println("URL: " + url);
                    System.out.println("Citação: " + meta.getCitation());
                } else {
                    // Fallback para quando não há descrições (ex: incoming links)
                    System.out.println(url);
                }
            }
            System.out.println("------------------------------------------------");

            System.out.println("1 - Próxima | 2 - Anterior | 3 - Voltar");
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.equals("1")) {
                if (end < total) currentPage++;
                else System.out.println("⚠ Fim da lista.");
            } else if (input.equals("2")) {
                if (currentPage > 0) currentPage--;
                else System.out.println("⚠ Início da lista.");
            } else if (input.equals("3")) {
                break;
            }
        }
    }

    private static boolean isValidURL(String url) {
        // Regex simples para validar http/https
        String regex = "^(https?|ftp)://[^\\s/$.?#].\\S*$";
        return Pattern.compile(regex).matcher(url).matches();
    }

    public static void main(String[] args) {
        if (connectToGateway()) {
            showMenu();
        } else {
            System.err.println("Não foi possível iniciar o cliente.");
        }
    }
}