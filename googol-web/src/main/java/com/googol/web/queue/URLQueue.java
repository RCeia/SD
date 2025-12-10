package com.googol.web.queue;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Scanner;
import com.googol.web.downloader.IDownloader;

public class URLQueue implements IQueue {
    private final Queue<String> urls = new LinkedList<>();
    private final Map<Integer, IDownloader> downloaders = new HashMap<>();
    private final Queue<Integer> availableDownloaders = new LinkedList<>();

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
            System.out.println("[Downloader" + id + "] - Disponível novamente.");
            assignWork();
        } else {
            System.err.println("[Queue] Downloader desconhecido (stub não registado).");
        }
    }

    private int getIdFromStub(IDownloader stub) {
        return downloaders.entrySet().stream()
                .filter(e -> e.getValue().equals(stub))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(-1);
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
                urls.add(url);
            }
        }
    }

    public synchronized void printAllURLs() throws RemoteException {
        if (urls.isEmpty()) System.out.println("[Queue] - Fila vazia.");
        else urls.forEach(u -> System.out.println("[Queue] " + u));
    }

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
            queue.addURL("https://rceia.github.io/SD/tests/index.html");

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
