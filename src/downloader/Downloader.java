package downloader;

import com.sun.security.jgss.GSSUtil;
import common.PageData;
import queue.IQueue;
import barrel.IBarrel;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.Connection;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;


public class Downloader implements IDownloader {
    private static int nextId = 1;
    private final int id;

    private IQueue queue;
    private List<IBarrel> barrels = new ArrayList<>();

    private Thread preProcessingThread;
    private Thread processingThread;
    private Thread sendingThread;

    public Downloader(IQueue queue) {
        super();
        this.queue = queue;
        this.id = (int) (ProcessHandle.current().pid() * 10 + nextId++);
        discoverBarrels();
    }

    @Override
    public void takeURL(String url) throws RemoteException {
        System.out.println("[Downloader" + id + "] - Recebi URL para download: " + url);

        if (barrels.getFirst().isUrlInBarrels(url)) {
            System.out.println("[Downloader" + id + "] - O URL já está no Barrel. " + url);
            notifyFinished();
        } else {
            download(url);
        }
    }

    private void sendToBarrels(PageData data) throws RemoteException {
        if (barrels.isEmpty()) {
            System.err.println("Nenhum Barrel disponível, URL será re-adicionado à Queue.");
            try {
                queue.addURL(data.getUrl());
            } catch (Exception e) {
                System.err.println("Erro ao re-adicionar URL à Queue: " + e.getMessage());
            }
            return;
        }

        System.out.println("\n [Downloader" + id + "] - A enviar página para os Barrels...");
        System.out.println("URL: " + data.getUrl());
        System.out.println("Título: " + data.getTitle());
        System.out.println("Palavras: " + data.getWords().size());
        System.out.println("Links encontrados: " + data.getOutgoingLinks().size());

        for (IBarrel barrel : barrels) {
            try {
                barrel.storePage(data);
                System.out.println("[Downloader" + id +"] - Enviado com sucesso para " + barrel);
            } catch (Exception e) {
                System.err.println("[Downloader" + id + "] - Falha ao enviar para um Barrel: " + e.getMessage());
            }
        }
    }

    @Override
    public void notifyFinished() throws RemoteException{
        System.out.println("[Downloader" + id + "] - Download finished, notifying Queue...");
        try {
            queue.notifyDownloaderAvailable(this);
        } catch (Exception e) {
            System.err.println("Erro ao notificar Queue: " + e.getMessage());
        }
    }

    private void download(String url) {
        new Thread(() -> {
            try {
                System.out.println("[Downloader" + id + "] - Downloading: " + url);

                if (!url.toLowerCase().endsWith(".html")) {
                    System.out.println("[Downloader" + id + "] - Este URL não respeita o formato permitido: " + url);
                    notifyFinished();
                    return;
                }

                Document doc = Jsoup.connect(url).get();


                String text = doc.body().text();
                if (text.trim().isEmpty()) {
                    System.out.println("[Downloader" + id + "] - A página está vazia ou não contém conteúdo útil: " + url);
                    notifyFinished();
                    return;
                }

                String title = doc.title();

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
                    System.out.println("[Downloader" + id + "] - Adicionado 1 novo URL à Queue.");
                } else if (!outgoingLinks.isEmpty()) {
                    queue.addURLs(outgoingLinks);
                    System.out.println("[Downloader" + id + "] - Adicionados " + outgoingLinks.size() + " novos URLs à Queue.");
                }

                notifyFinished();

            } catch (Exception e) {
                System.err.println("[Downloader" + id + "] - Erro ao processar URL: " + url + ". Será re-adicionado à Queue.");
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
            String serverIP = args.length > 0 ? args[0] : "localhost";
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

            Registry registry = LocateRegistry.getRegistry(serverIP, port);
            IQueue queue = (IQueue) registry.lookup("URLQueueInterface");

            Downloader downloader = new Downloader(queue);
            IDownloader stub = (IDownloader) UnicastRemoteObject.exportObject(downloader, 0);

            queue.registerDownloader(stub, downloader.id);
            System.out.println("[Downloader" + downloader.id + "] - Registado e pronto para receber URLs.");

            synchronized (Downloader.class) {
                Downloader.class.wait();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
