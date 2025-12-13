package queue;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Scanner;
import downloader.IDownloader;

/**
 * Implementação da fila de URLs (URLQueue) utilizando RMI (Remote Method Invocation).
 * <p>
 * Esta classe gere uma lista de URLs a serem processados e distribui tarefas
 * por downloaders registados à medida que estes ficam disponíveis.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class URLQueue implements IQueue {

    /**
     * Fila que armazena as URLs (Strings) pendentes para processamento.
     */
    private final Queue<String> urls = new LinkedList<>();

    /**
     * Mapa que associa um identificador (ID) à interface remota do Downloader.
     */
    private final Map<Integer, IDownloader> downloaders = new HashMap<>();

    /**
     * Fila que armazena os IDs dos downloaders que estão atualmente livres/disponíveis.
     */
    private final Queue<Integer> availableDownloaders = new LinkedList<>();

    /**
     * Adiciona um único URL à fila de processamento.
     * Após adicionar, tenta atribuir trabalho aos downloaders disponíveis.
     *
     * @param url A string contendo o URL a ser adicionado.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    @Override
    public synchronized void addURL(String url) throws RemoteException {
        urls.add(url);
        System.out.println("[Queue] - URL adicionado: " + url);
        assignWork();
    }

    /**
     * Adiciona uma lista de URLs à fila de processamento.
     * Após adicionar, tenta atribuir trabalho aos downloaders disponíveis.
     *
     * @param newUrls Lista de strings contendo os URLs a serem adicionados.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    @Override
    public synchronized void addURLs(List<String> newUrls) throws RemoteException {
        urls.addAll(newUrls);
        System.out.println("[Queue] - URLs adicionados: " + newUrls.size());
        assignWork();
    }

    /**
     * Obtém o tamanho atual da fila de URLs.
     *
     * @return O número de URLs atualmente na fila.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    @Override
    public synchronized int getQueueSize() throws RemoteException {
        return urls.size();
    }

    /**
     * Retira e retorna o próximo URL da fila.
     *
     * @return A string do próximo URL, ou null se a fila estiver vazia.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    @Override
    public synchronized String getNextURL() throws RemoteException {
        return urls.poll();
    }

    /**
     * Regista um novo Downloader no sistema e marca-o como disponível.
     *
     * @param downloader A referência para o objeto remoto do Downloader.
     * @param id O identificador único do Downloader.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    @Override
    public synchronized void registerDownloader(IDownloader downloader, int id) throws RemoteException {
        downloaders.put(id, downloader);
        availableDownloaders.add(id);
        System.out.println("[Queue] - Downloader " + id + " registado e disponível.");
        assignWork();
    }

    /**
     * Notifica a Queue de que um Downloader específico concluiu a sua tarefa e está disponível.
     *
     * @param downloader A referência para o objeto remoto do Downloader que ficou livre.
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    @Override
    public synchronized void notifyDownloaderAvailable(IDownloader downloader) throws RemoteException {
        int id = getIdFromStub(downloader);
        if (id != -1) {
            availableDownloaders.add(id);
            System.out.println("[Downloader" + id + "] - Disponível novamente.");
            assignWork();
        } else {
            System.err.println("[Queue] Downloader desconhecido (stub não registado).");
        }
    }

    /**
     * Método auxiliar privado para encontrar o ID de um Downloader com base na sua referência (stub).
     *
     * @param stub A referência do objeto remoto a procurar.
     * @return O ID do downloader se encontrado, ou -1 se não existir.
     */
    private int getIdFromStub(IDownloader stub) {
        return downloaders.entrySet().stream()
                .filter(e -> e.getValue().equals(stub))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(-1);
    }

    /**
     * Método auxiliar privado que atribui URLs pendentes aos Downloaders disponíveis.
     * Executa enquanto houver URLs na fila e Downloaders livres.
     * Se falhar a comunicação com um Downloader, o URL é devolvido à fila.
     */
    private synchronized void assignWork() {
        while (!urls.isEmpty() && !availableDownloaders.isEmpty()) {
            int id = availableDownloaders.poll();
            IDownloader downloader = downloaders.get(id);
            String url = urls.poll();
            try {
                System.out.println("[Queue] Atribuído URL a Downloader " + id + ": " + url);
                downloader.takeURL(url);
            } catch (RemoteException e) {
                System.err.println("[Queue] Falha ao contactar Downloader " + id + ": " + e.getMessage());
                urls.add(url);
            }
        }
    }

    /**
     * Imprime no ecrã todos os URLs atualmente na fila.
     *
     * @throws RemoteException Se ocorrer um erro na comunicação RMI.
     */
    public synchronized void printAllURLs() throws RemoteException {
        if (urls.isEmpty()) System.out.println("[Queue] - Fila vazia.");
        else urls.forEach(u -> System.out.println("[Queue] " + u));
    }

    /**
     * Método principal (Main) que inicializa o servidor RMI da Queue.
     * Configura o registry, exporta o objeto remoto e aguarda comandos do utilizador.
     *
     * @param args Argumentos de linha de comando: [0] IP do host, [1] Porta do registry.
     */
    public static void main(String[] args) {
        try {
            // Define o IP e porta (sem ifs)
            String hostIP = args.length > 0 ? args[0] : InetAddress.getLocalHost().getHostAddress();
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

            System.setProperty("java.rmi.server.hostname", hostIP);

            // Cria e exporta o objeto remoto
            URLQueue queue = new URLQueue();
            Registry registry = LocateRegistry.createRegistry(port);
            IQueue stub = (IQueue) UnicastRemoteObject.exportObject(queue, 0);
            registry.rebind("URLQueueInterface", stub);

            System.out.println("Queue ready on " + hostIP + ":" + port);
            // queue.addURL("https://www.uc.pt");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.println("\nShutting down Queue...");
                    UnicastRemoteObject.unexportObject(queue, true);
                    System.out.println("Queue stopped.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));

            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("\nDigite 'show' ou 'exit': ");
                String cmd = scanner.nextLine().trim().toLowerCase();
                if (cmd.equals("show")) queue.printAllURLs();
                else if (cmd.equals("exit")) break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}