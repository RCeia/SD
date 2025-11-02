package client;

import gateway.IGateway;
import common.RetryLogic;

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
    private static Map<String, String> lastSearchDescriptions = new HashMap<>();
    private static List<String> lastIncomingLinks = new ArrayList<>();

    // Conectar à Gateway
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
                    System.err.println("Gateway não encontrada no host " + host + ": " + e.getMessage());
                }

                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return false;
    }

    public static boolean reconnectToGateway() {
        System.out.println("Tentando reconectar à Gateway...");
        boolean success = connectToGateway();
        if (success) System.out.println("Reconexão bem-sucedida!");
        else System.err.println("Falha ao reconectar à Gateway.");
        return success;
    }

    // Menu principal
    // Menu principal (corrigido com verificação de input)
    public static void showMenu() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nEscolha a operação:");
            System.out.println("1 - Indexar URL");
            System.out.println("2 - Pesquisar páginas");
            System.out.println("3 - Consultar links para uma página");
            System.out.println("4 - Obter estatísticas do sistema");
            System.out.println("5 - Sair");
            System.out.print("Opção: ");

            int choice;

            try {
                choice = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida. Por favor insira um número de 1 a 5.");
                continue; // volta ao menu
            }

            switch (choice) {
                case 1 -> {
                    System.out.print("Digite o URL: ");
                    String url = scanner.nextLine();
                    indexURL(url);
                }
                case 2 -> {
                    System.out.print("Digite o termo de pesquisa: ");
                    String term = scanner.nextLine();
                    searchPages(term);
                }
                case 3 -> {
                    System.out.print("Digite o URL para verificar links: ");
                    String url = scanner.nextLine();
                    getIncomingLinks(url);
                }
                case 4 -> getSystemStats();
                case 5 -> {
                    System.out.println("Saindo...");
                    return;
                }
                default -> System.out.println("Opção inválida. Por favor insira um número entre 1 e 5.");
            }
        }
    }


    // Indexar URL
    public static void indexURL(String url) {
        if (!isValidURL(url)) {
            System.err.println("URL inválido.");
            return;
        }

        try {
            String result = RetryLogic.executeWithRetry(
                    RETRY_LIMIT,
                    RETRY_DELAY,
                    Client::reconnectToGateway,
                    () -> gateway.indexURL(url)
            );
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("Erro ao indexar URL: " + e.getMessage());
        }
    }

    // Pesquisa de páginas (com paginação)
    public static void searchPages(String searchTerm) {
        try {
            List<String> terms = Arrays.asList(searchTerm.trim().split("\\s+"));

            if (gateway != null) {
                Map<String, String> results = gateway.search(terms);

                if (results == null || results.isEmpty()) {
                    System.out.println("Nenhum resultado encontrado.");
                    return;
                }

                lastSearchResults = new ArrayList<>(results.keySet());
                lastSearchDescriptions = results;

                showPagedResults(lastSearchResults, lastSearchDescriptions, "pesquisa");
            } else {
                System.err.println("Gateway não está conectada.");
            }
        } catch (RemoteException e) {
            System.err.println("Erro ao realizar a pesquisa: " + e.getMessage());
        }
    }

    // Links de entrada (com paginação)
    public static void getIncomingLinks(String url) {
        try {
            List<String> links = RetryLogic.executeWithRetry(
                    RETRY_LIMIT,
                    RETRY_DELAY,
                    Client::reconnectToGateway,
                    () -> gateway.getIncomingLinks(url)
            );

            if (links == null || links.isEmpty()) {
                System.out.println("Nenhum link encontrado.");
                return;
            }

            lastIncomingLinks = links;
            showPagedResults(lastIncomingLinks, null, "links");

        } catch (Exception e) {
            System.err.println("Erro ao consultar links: " + e.getMessage());
        }
    }

    // Mostrar resultados paginados
    private static void showPagedResults(List<String> results, Map<String, String> descriptions, String type) {
        Scanner scanner = new Scanner(System.in);
        int total = results.size();
        int pageSize = 10;
        int currentPage = 0;

        while (true) {
            int start = currentPage * pageSize;
            int end = Math.min(start + pageSize, total);
            List<String> page = results.subList(start, end);

            System.out.println("\n=== Resultados " + type + " (" + (start + 1) + " a " + end + " de " + total + ") ===");
            for (String item : page) {
                if (descriptions != null)
                    System.out.println("URL: " + item + " | " + descriptions.get(item));
                else
                    System.out.println(item);
            }

            System.out.println("\n1 - Próxima página | 2 - Página anterior | 3 - Voltar ao menu");
            System.out.print("Opção: ");

            String input = scanner.nextLine().trim();

            if (input.equals("1")) {
                if (end < total) currentPage++;
                else System.out.println("Não há mais páginas.");
            } else if (input.equals("2")) {
                if (currentPage > 0) currentPage--;
                else System.out.println("Já está na primeira página.");
            } else if (input.equals("3")) {
                break; // volta ao menu principal
            } else {
                System.out.println("Entrada inválida. Por favor insira 1, 2 ou 3.");
            }
        }
    }

    // Estatísticas
    public static void getSystemStats() {
        try {
            String stats = RetryLogic.executeWithRetry(
                    RETRY_LIMIT,
                    RETRY_DELAY,
                    Client::reconnectToGateway,
                    () -> gateway.getSystemStats()
            );
            System.out.println(stats);
        } catch (Exception e) {
            System.err.println("Erro ao obter estatísticas: " + e.getMessage());
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
            System.err.println("Não foi possível conectar à Gateway.");
        }
    }
}
