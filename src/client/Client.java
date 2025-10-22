package client;

import gateway.IGateway;

import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.List;
import java.util.Scanner;

public class Client {

    private static final int RETRY_LIMIT = 3;      // Número máximo de tentativas
    private static final long RETRY_DELAY = 1000;  // Delay entre tentativas (5 segundos)
    private static final String[] GATEWAY_HOSTS = {"localhost", "backupGateway"};  // Lista de Gateways para Failover
    private static IGateway gateway = null;

    // Método para conectar à Gateway com retry e failover
    public static boolean connectToGateway() {
        int retries = 0;
        while (retries < RETRY_LIMIT) {
            for (String host : GATEWAY_HOSTS) {
                try {
                    System.out.println("Tentando conectar à Gateway em " + host + "...");
                    Registry registry = LocateRegistry.getRegistry(host, 1099);
                    gateway = (IGateway) registry.lookup("Gateway");
                    System.out.println("Conectado com sucesso à Gateway em " + host);
                    return true;  // Conexão bem-sucedida
                } catch (RemoteException e) {
                    System.err.println("Falha ao conectar à Gateway em " + host + ". Tentando novamente...");
                    retries++;
                    if (retries >= RETRY_LIMIT) {
                        System.err.println("Falha ao conectar à Gateway após várias tentativas.");
                        return false;  // Falhou após múltiplas tentativas
                    }
                    try {
                        Thread.sleep(RETRY_DELAY);  // Aguardar antes de tentar novamente
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        System.err.println("Erro durante a espera entre tentativas.");
                    }
                } catch (NotBoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return false;
    }

    // Método para exibir o menu de opções
    public static void showMenu() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nEscolha a operação que deseja realizar:");
            System.out.println("1 - Indexar um novo URL");
            System.out.println("2 - Pesquisar páginas");
            System.out.println("3 - Consultar links para uma página");
            System.out.println("4 - Sair");
            System.out.print("Digite o número da operação: ");
            int choice = scanner.nextInt();
            scanner.nextLine();  // Limpar o buffer de entrada

            switch (choice) {
                case 1:
                    System.out.print("Digite o URL para indexar: ");
                    String url = scanner.nextLine();
                    indexURL(url);
                    break;
                case 2:
                    System.out.print("Digite o termo de pesquisa: ");
                    String searchTerm = scanner.nextLine();
                    searchPages(searchTerm);
                    break;
                case 3:
                    System.out.print("Digite o URL para consultar links apontando para ele: ");
                    String targetUrl = scanner.nextLine();
                    getIncomingLinks(targetUrl);
                    break;
                case 4:
                    System.out.println("Saindo...");
                    return;  // Encerra o menu
                default:
                    System.out.println("Opção inválida. Tente novamente.");
            }
        }
    }
    // Método genérico para retry
    private static <T> T retryOperation(int retries, long delay, Operation<T> operation) throws InterruptedException {
        int attempt = 0;
        while (attempt < retries) {
            try {
                // Tenta realizar a operação
                return operation.execute();
            } catch (RemoteException e) {
                // Em caso de falha, imprime erro e tenta novamente
                System.err.println("Falha ao realizar a operação. Tentando novamente... " + e.getMessage());
                attempt++;
                if (attempt >= retries) {
                    throw new RuntimeException("Falha ao completar a operação após várias tentativas.", e);
                }
                Thread.sleep(delay);  // Aguardar antes de tentar novamente
            }
        }
        return null;
    }

    // Interface funcional para as operações (indexação, pesquisa, etc.)
    @FunctionalInterface
    interface Operation<T> {
        T execute() throws RemoteException;
    }

    // Método para indexar um novo URL com retry
    public static void indexURL(String url) {
        try {
            String status = retryOperation(RETRY_LIMIT, RETRY_DELAY, () -> {
                if (gateway != null) {
                    return gateway.indexURL(url);  // Realiza a indexação do URL
                }
                return null;
            });
            System.out.println(status);
        } catch (RuntimeException | InterruptedException e) {
            System.err.println("Erro ao indexar o URL: " + e.getMessage());
        }
    }

    // Método para realizar uma pesquisa com retry
    public static void searchPages(String searchTerm) {
        try {
            Map<String, String> results = retryOperation(RETRY_LIMIT, RETRY_DELAY, () -> {
                if (gateway != null) {
                    return gateway.search(searchTerm);  // Realiza a pesquisa
                }
                return null;
            });
            assert results != null;
            if (results.isEmpty()) {
                System.out.println("Nenhum resultado encontrado.");
            } else {
                System.out.println("Resultados da pesquisa:");
                results.forEach((url, description) -> System.out.println("URL: " + url + " | Description: " + description));
            }
        } catch (RuntimeException | InterruptedException e) {
            System.err.println("Erro ao realizar a pesquisa: " + e.getMessage());
        }
    }

    // Método para consultar links para uma página com retry
    public static void getIncomingLinks(String url) {
        try {
            List<String> links = retryOperation(RETRY_LIMIT, RETRY_DELAY, () -> {
                if (gateway != null) {
                    return gateway.getIncomingLinks(url);  // Consulta os links
                }
                return null;
            });
            assert links != null;
            if (links.isEmpty()) {
                System.out.println("Nenhum link encontrado para a página.");
            } else {
                System.out.println("Links apontando para a página '" + url + "':");
                links.forEach(System.out::println);
            }
        } catch (RuntimeException | InterruptedException e) {
            System.err.println("Erro ao consultar links: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        // Tentar conectar à Gateway com retry e failover
        if (connectToGateway()) {
            showMenu();  // Exibe o menu de opções se a conexão for bem-sucedida
        } else {
            System.err.println("Não foi possível conectar à Gateway após várias tentativas.");
        }
    }
}
