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
import barrel.IBarrel;
import java.util.ArrayList;


public class Downloader implements IDownloader {

    private IQueue queue;
    private List<IBarrel> barrels = new ArrayList<>();


    public Downloader(IQueue queue) {
        super();
        this.queue = queue;
        discoverBarrels();

    }

    @Override
    public void takeURL(String url) throws RemoteException{
        System.out.println("Recebi URL para download: " + url);
        download(url);
    }

    private void sendToBarrels(PageData data) throws RemoteException {
        if (barrels.isEmpty()) {
            System.err.println("Nenhum Barrel disponível — URL será re-adicionado à Queue.");
            try {
                queue.addURL(data.getUrl());
            } catch (Exception e) {
                System.err.println("Erro ao re-adicionar URL à Queue: " + e.getMessage());
            }
            return;
        }

        System.out.println("\nA enviar página para os Barrels...");
        System.out.println("URL: " + data.getUrl());
        System.out.println("Título: " + data.getTitle());
        System.out.println("Palavras: " + data.getWords().size());
        System.out.println("Links encontrados: " + data.getOutgoingLinks().size());

        for (IBarrel barrel : barrels) {
            try {
                barrel.storePage(data);
                System.out.println("Enviado com sucesso para " + barrel);
            } catch (Exception e) {
                System.err.println("Falha ao enviar para um Barrel: " + e.getMessage());
            }
        }

        System.out.println("Envio concluído.\n");
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

    private void download(String url) {
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

                sendToBarrels(pageData);

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

    private void discoverBarrels() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            String[] boundNames = registry.list();

            for (String bound : boundNames) {
                if (bound.startsWith("Barrel")) {
                    IBarrel barrel = (IBarrel) registry.lookup(bound);
                    barrels.add(barrel);
                    System.out.println("Ligado ao " + bound);
                }
            }

            if (barrels.isEmpty()) {
                System.out.println("Nenhum Barrel encontrado no RMI Registry!");
            }

        } catch (Exception e) {
            System.err.println("Erro na descoberta de Barrels: " + e.getMessage());
        }
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
