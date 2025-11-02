package queue;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Scanner;

import downloader.IDownloader;

public class URLQueue implements IQueue {
    private final Queue<String> urls;
    private final Map<Integer, IDownloader> downloaders;
    private final Queue<Integer> availableDownloaders;

    public URLQueue() {
        super();
        this.urls = new LinkedList<>();
        this.availableDownloaders = new LinkedList<>();
        this.downloaders = new HashMap<>();
    }

    @Override
    public synchronized void addURL(String url) throws RemoteException {
        urls.add(url);
        System.out.println("[Queue] - URL adicionado: " + url);
        assignWork();
    }

    @Override
    public synchronized void addURLs(List<String> newUrls) throws RemoteException {
        urls.addAll(newUrls);
        System.out.println("[Queue] - URLs adicionados: " + newUrls.size());
        assignWork();
    }

    @Override
    public synchronized int getQueueSize() throws RemoteException {
        return urls.size();
    }

    @Override
    public synchronized String getNextURL() throws RemoteException {
        return urls.poll();
    }

    @Override
    public synchronized void registerDownloader(IDownloader downloader, int id) throws RemoteException {
        downloaders.put(id, downloader);
        availableDownloaders.add(id);
        System.out.println("[Queue] - Downloader " + id + " registado e disponível.");
        assignWork();
    }

    @Override
    public synchronized void notifyDownloaderAvailable(IDownloader downloader) throws RemoteException {
        int id = getIdFromStub(downloader);
        if (id != -1) {
            availableDownloaders.add(id);
            System.out.println("[Downloader" + id + "] -  Disponível novamente.");
            assignWork();
        } else {
            System.err.println("[Queue] Downloader desconhecido (stub não registado).");
        }
    }

    private int getIdFromStub(IDownloader stub) {
        for (var entry : downloaders.entrySet()) {
            if (entry.getValue().equals(stub)) {
                return entry.getKey();
            }
        }
        return -1;
    }

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
                urls.add(url); // devolve o URL
            }
        }
    }

    public synchronized void printAllURLs() throws RemoteException {
        if (urls.isEmpty()) {
            System.out.println("[Queue] A fila de URLs está vazia.");
        } else {
            System.out.println("[Queue] URLs na fila:");
            for (String url : urls) {
                System.out.println(url);
            }
        }
    }

    public static void main(String[] args) {
        try {
            // Ler IP (hostname) e porta
            String hostIP = args.length > 0 ? args[0] : "0.0.0.0";
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

            // Define o IP que o RMI vai anunciar aos clientes
            System.setProperty("java.rmi.server.hostname", InetAddress.getLocalHost().getHostAddress());

            // Criar instância da Queue
            URLQueue queue = new URLQueue();

            // Criar Registry local
            Registry registry = LocateRegistry.createRegistry(port);

            // Exportar e registar o objeto remoto
            IQueue stub = (IQueue) UnicastRemoteObject.exportObject(queue, 0);
            registry.rebind("URLQueueInterface", stub);

            System.out.println("Queue ready on " + hostIP + ":" + port);

            // Adicionar URLs iniciais
            queue.addURL("https://www.google.com");

            // Hook de shutdown para limpar
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.println("\nShutting down Queue...");
                    UnicastRemoteObject.unexportObject(queue, true);
                    System.out.println("Queue stopped.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));

            System.out.println("Queue is ready. Downloaders can now register.");

            // Criação de um scanner para o terminal
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("\nDigite 'show' para ver os URLs da fila ou 'exit' para sair.");
                String command = scanner.nextLine().trim().toLowerCase();

                if ("show".equals(command)) {
                    queue.printAllURLs();  // Chama o método para imprimir as URLs
                } else if ("exit".equals(command)) {
                    System.out.println("[Queue] Saindo...");
                    break;  // Encerra o loop e finaliza a aplicação
                } else {
                    System.out.println("Comando desconhecido. Tente novamente.");
                }
            }

            // Manter o servidor ativo até que o usuário digite 'exit'
            synchronized (URLQueue.class) {
                URLQueue.class.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
