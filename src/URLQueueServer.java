import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class URLQueueServer extends UnicastRemoteObject implements URLQueueInterface {

    private final Deque<String> queue = new ArrayDeque<>();
    private final List<DownloaderCallback> listeners = new CopyOnWriteArrayList<>();

    public URLQueueServer() throws RemoteException {
        super();
        System.out.println("[Queue] Inicializada (modo esqueleto).");
    }

    @Override
    public synchronized void addUrl(String url) throws RemoteException {
        queue.addLast(url);
        System.out.println("[Queue] URL adicionado: " + url + " | size=" + queue.size());
        notifyListeners();
    }

    @Override
    public void registerDownloader(DownloaderCallback downloader) throws RemoteException {
        listeners.add(downloader);
        System.out.println("[Queue] Downloader registado: " + safeId(downloader));
        // notifica imediatamente caso já haja URLs
        if (!queue.isEmpty()) {
            try { downloader.notifyUrlAvailable(); } catch (Exception ignored) {}
        }
    }

    @Override
    public synchronized String getNextUrl() throws RemoteException {
        String u = queue.pollFirst();
        System.out.println("[Queue] getNextUrl -> " + u + " | size=" + queue.size());
        return u;
    }

    @Override
    public synchronized int size() throws RemoteException {
        return queue.size();
    }

    @Override
    public String ping() throws RemoteException {
        return "Queue up";
    }

    private void notifyListeners() {
        for (DownloaderCallback d : listeners) {
            try {
                d.notifyUrlAvailable();
            } catch (Exception e) {
                System.out.println("[Queue] Falha a notificar downloader (" + safeId(d) + "): " + e.getMessage());
            }
        }
    }

    private String safeId(DownloaderCallback d) {
        try { return d.getId(); } catch (Exception e) { return "unknown"; }
    }

    public static void main(String[] args) {
        try {
            String name = args.length > 0 ? args[0] : "Queue";
            URLQueueServer srv = new URLQueueServer();
            Naming.rebind(name, srv);
            System.out.println("[Queue] Bound no RMI como '" + name + "'");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
