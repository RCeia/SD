package queue;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLOutput;
import java.util.*;

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

    public static void main(String[] args) {
        try {
            // Criar instância da Queue
            URLQueue queue = new URLQueue();

            // Criar e exportar o objeto remoto
            IQueue stub = (IQueue) UnicastRemoteObject.exportObject(queue, 0);

            // Criar (ou obter) o Registry na porta 1099
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(1099);
                System.out.println("[Queue] - Registry criado na porta 1099.");
            } catch (RemoteException e) {
                registry = LocateRegistry.getRegistry(1099);
                System.out.println("[Queue] - Ligado ao Registry existente.");
            }

            // Registar a Queue no Registry
            registry.rebind("URLQueueInterface", stub);
            System.out.println("[Queue] - Queue registada no RMI Registry como 'URLQueueInterface'.");

            // Adicionar URLs iniciais
            //queue.addURL("https://rceia.github.io/SD/tests/index.html");
            queue.addURL("https://www.google.com");

            System.out.println("[Queue] - Queue pronta e à escuta...");

            // Manter servidor ativo
            synchronized (URLQueue.class) {
                URLQueue.class.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
