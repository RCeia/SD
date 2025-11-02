package adaptivestopwords;

import org.jsoup.Jsoup;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AdaptiveStopWords extends UnicastRemoteObject implements IAdaptiveStopWords {

    private final Map<String, Integer> docFrequency = new HashMap<>();
    private final Set<String> processedURLs = ConcurrentHashMap.newKeySet();

    public AdaptiveStopWords() throws RemoteException {
        super();
    }

    @Override
    public synchronized void processDoc(String url, Set<String> uniqueWords) throws RemoteException {
        if (processedURLs.add(url)) {
            for (String word : uniqueWords) {
                docFrequency.merge(word, 1, Integer::sum);
            }
            System.out.println("[AdaptiveStopWords] URL " + url + " processado. Total de documentos: " + docFrequency.size());
        }
    }

    @Override
    public Set<String> getStopWords(double threshold) throws RemoteException {
        Set<String> stopWords = new HashSet<>();
        long totalDocs = processedURLs.size();
        if (totalDocs == 0) {
            return stopWords; // Ainda não processou URLs por isso não aprendeu nenhuma stop word
        }

        for (Map.Entry<String, Integer> entry : docFrequency.entrySet()) {
            double dfRatio = (double) entry.getValue() / totalDocs;
            if (dfRatio > threshold) {
                stopWords.add(entry.getKey());
            }
        }
        return stopWords;
    }

    @Override
    public boolean isURLprocessed(String url) throws RemoteException {
        return processedURLs.contains(url);
    }

    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 1099;

            AdaptiveStopWords adaptiveStopWords = new AdaptiveStopWords();
            Registry registry = LocateRegistry.getRegistry("localhost", port);
            registry.rebind("AdaptiveStopWords", adaptiveStopWords);

            System.out.println("[AdaptiveStopWords] Serviço de identificação de stop words registado e pronto.");

            synchronized (AdaptiveStopWords.class) {
                AdaptiveStopWords.class.wait();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
