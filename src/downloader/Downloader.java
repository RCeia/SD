package downloader;

import common.PageData;
import queue.IQueue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.List;
import java.util.stream.Collectors;

public class Downloader implements IDownloader {

    private IQueue queue;

    public Downloader(IQueue queue) {
        super();
        this.queue = queue;
    }

    @Override
    public void takeURL(String url) throws RemoteException{
        System.out.println("Recebi URL para download: " + url);
        download(url);
    }

    @Override
    public void sendToBarrels(PageData data) throws RemoteException{

    }

    @Override
    public void notifyFinished() throws RemoteException{
        System.out.println("Download finished - notifying Queue...");
        try {
            queue.notifyDownloaderAvailable(this);
        } catch (Exception e) {
            System.err.println("Erro ao notificar Queue: " + e.getMessage());
        }
    }

    @Override
    public void download(String url) {
        new Thread(() -> {
            try {
                System.out.println("A descarregar: " + url);

                Document doc = Jsoup.connect(url).get();

                String title = doc.title();
                String text = doc.body().text();

                List<String> words = List.of(text.split("\\s+"));

                Elements links = doc.select("a[href]");
                List<String> outgoingLinks = links.stream()
                        .map(link -> link.absUrl("href"))
                        .filter(href -> !href.isEmpty())
                        .collect(Collectors.toList());

                PageData pageData = new PageData(url, title, words, outgoingLinks);

                if (outgoingLinks.size() == 1) {
                    queue.addURL(outgoingLinks.get(0));
                    System.out.println("Adicionado 1 novo URL à Queue.");
                } else if (!outgoingLinks.isEmpty()) {
                    queue.addURLs(outgoingLinks);
                    System.out.println("Adicionados " + outgoingLinks.size() + " novos URLs à Queue.");
                }

                notifyFinished();

            } catch (Exception e) {
                System.err.println("Erro ao processar URL: " + url + " -> será re-adicionado à Queue.");
                try {
                    queue.addURL(url);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            IQueue queue = (IQueue) registry.lookup("URLQueueInterface");

            Downloader downloader = new Downloader(queue);
            IDownloader stub = (IDownloader) UnicastRemoteObject.exportObject(downloader, 0);

            queue.registerDownloader(stub);
            System.out.println("Downloader registado e pronto para receber URLs.");

            synchronized (Downloader.class) {
                Downloader.class.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
