package client;

import gateway.IGateway;
import common.RetryLogic;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Client {

    private static final int RETRY_LIMIT = 3;
    private static final long RETRY_DELAY = 1000;
    private static final String[] GATEWAY_HOSTS = {"localhost", "backupGateway"};
    private static IGateway gateway = null;

    // Connect to any Gateway in the list
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

    // Menu
    public static void showMenu() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nEscolha a operação:");
            System.out.println("1 - Indexar URL");
            System.out.println("2 - Pesquisar páginas");
            System.out.println("3 - Consultar links para uma página");
            System.out.println("4 - Sair");
            System.out.print("Opção: ");

            int choice = scanner.nextInt();
            scanner.nextLine(); // limpar buffer

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
                case 4 -> {
                    System.out.println("Saindo...");
                    return;
                }
                default -> System.out.println("Opção inválida.");
            }
        }
    }

    // Index URL
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

    // Search pages
    public static void searchPages(String term) {
        try {
            Map<String, String> results = RetryLogic.executeWithRetry(
                    RETRY_LIMIT,
                    RETRY_DELAY,
                    Client::reconnectToGateway,
                    () -> gateway.search(term)
            );

            if (results == null || results.isEmpty()) {
                System.out.println("Nenhum resultado encontrado.");
                return;
            }

            System.out.println("Resultados:");
            results.forEach((url, desc) -> System.out.println("URL: " + url + " | " + desc));

        } catch (Exception e) {
            System.err.println("Erro ao pesquisar: " + e.getMessage());
        }
    }

    // Get incoming links
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

            System.out.println("Links apontando para '" + url + "':");
            links.forEach(System.out::println);

        } catch (Exception e) {
            System.err.println("Erro ao consultar links: " + e.getMessage());
        }
    }

    private static boolean isValidURL(String url) {
        String regex = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$";
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
