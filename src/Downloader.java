import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class Downloader extends UnicastRemoteObject implements URLQueueInterface.DownloaderCallback {

    private final String id;
    private URLQueueInterface queue;
    private BarrelInterface barrel;

    public Downloader(String id) throws RemoteException {
        super();
        this.id = id;
        System.out.println("[Downloader-" + id + "] Inicializado (modo esqueleto).");
    }

    private void lookupRemotes(String queueName, String barrelName) {
        try {
            this.queue = (URLQueueInterface) Naming.lookup(queueName);
            System.out.println("[Downloader-" + id + "] Conectado à Queue: " + queueName);
        } catch (Exception e) {
            System.out.println("[Downloader-" + id + "] Falha ao ligar à Queue: " + e.getMessage());
        }
        try {
            this.barrel = (BarrelInterface) Naming.lookup(barrelName);
            System.out.println("[Downloader-" + id + "] Conectado ao Barrel: " + barrelName);
        } catch (Exception e) {
            System.out.println("[Downloader-" + id + "] Falha ao ligar ao Barrel: " + e.getMessage());
        }
    }

    @Override
    public String getId() throws RemoteException {
        return id;
    }

    @Override
    public void notifyUrlAvailable() throws RemoteException {
        System.out.println("[Downloader-" + id + "] Notificado: URL disponível.");
        try {
            String url = queue.getNextUrl();
            if (url == null) {
                System.out.println("[Downloader-" + id + "] Nada para processar.");
                return;
            }
            // processamento SIMULADO (sem jsoup)
            String title = "Titulo Falso";
            String snippet = "Snippet simulado...";
            Set<String> words = new HashSet<>(Arrays.asList("foo", "bar", "baz"));
            Set<String> out = new HashSet<>(Arrays.asList("http://link1", "http://link2"));

            System.out.println("[Downloader-" + id + "] 'Processado' " + url + " -> enviando ao Barrel...");
            if (barrel != null) {
                barrel.addPageData(url, title, snippet, words, out);
            } else {
                System.out.println("[Downloader-" + id + "] Barrel indisponível.");
            }
        } catch (Exception e) {
            System.out.println("[Downloader-" + id + "] Erro ao consumir URL: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            String id = args.length > 0 ? args[0] : UUID.randomUUID().toString().substring(0, 8);
            String queueName = args.length > 1 ? args[1] : "Queue";
            String barrelName = args.length > 2 ? args[2] : "Barrel1";
            Downloader d = new Downloader(id);
            d.lookupRemotes(queueName, barrelName);

            if (d.queue != null) {
                d.queue.registerDownloader(d);
            } else {
                System.out.println("[Downloader-" + id + "] Não registado: Queue indisponível.");
            }

            System.out.println("[Downloader-" + id + "] Aguardando notificações...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
