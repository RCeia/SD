package queue;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLOutput;
import java.util.LinkedList;
import java.util.Queue;
import java.util.List;
import java.util.Arrays;

import downloader.IDownloader;

public class URLQueue implements IQueue {
    private final Queue<String> urls;
    private final Queue<IDownloader> availableDownloaders;

    public URLQueue() {
        super();
        this.urls = new LinkedList<>();
        this.availableDownloaders = new LinkedList<>();
    }

    @Override
    public synchronized void addURL(String url) throws RemoteException {
        urls.add(url);
        System.out.println("URL adicionado: " + url);
        assignWork();
    }

    @Override
    public synchronized void addURLs(List<String> newUrls) throws RemoteException {
        urls.addAll(newUrls);
        System.out.println("URLs adicionados: " + newUrls.size());
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
    public synchronized void registerDownloader(IDownloader downloader) throws RemoteException {
        System.out.println("Downloader registado: " + downloader);
        availableDownloaders.add(downloader);
        assignWork();
    }
    @Override
    public synchronized void notifyDownloaderAvailable(IDownloader downloader) throws RemoteException {
        System.out.println("Downloader disponível novamente: " + downloader);
        availableDownloaders.add(downloader);
        assignWork();
    }

    private synchronized void assignWork() {
        while (!urls.isEmpty() && !availableDownloaders.isEmpty()) {
            IDownloader downloader = availableDownloaders.poll();
            String url = urls.poll();

            try {
                downloader.takeURL(url);
                System.out.println("URL atribuído ao downloader: " + url);
            } catch (RemoteException e) {
                System.err.println("Falha ao contactar downloader: " + e.getMessage());
                // devolve o URL à fila, pois não foi processado
                urls.add(url);
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
                System.out.println("Registry criado na porta 1099.");
            } catch (RemoteException e) {
                registry = LocateRegistry.getRegistry(1099);
                System.out.println("Ligado ao Registry existente.");
            }

            // Registar a Queue no Registry
            registry.rebind("URLQueueInterface", stub);
            System.out.println("Queue registada no RMI Registry como 'URLQueueInterface'.");

            // Adicionar URLs iniciais
            queue.addURL("https://www.google.com");
            queue.addURLs(List.of("https://www.amazon.com", "https://www.uc.pt"));

            System.out.println("Queue pronta e à escuta...");

            // Manter servidor ativo
            synchronized (URLQueue.class) {
                URLQueue.class.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
