package adaptivestopwords;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AdaptiveStopWords extends UnicastRemoteObject implements IAdaptiveStopWords {

    private static final long MIN_DOCS_TO_LEARN = 100;
    private static final double STOPWORD_THRESHOLD = 0.9;
    private final Map<String, Integer> docFrequency = new HashMap<>();
    private int processedDocs = 0;

    public AdaptiveStopWords() throws RemoteException {
        super();
    }

    @Override
    public synchronized void processDoc(String url, Set<String> uniqueWords) throws RemoteException {
        processedDocs++;
        for (String word : uniqueWords) {
            docFrequency.merge(word, 1, Integer::sum);
        }
        System.out.println("[AdaptiveStopWords] URL " + url + " processado. Total de documentos: " + processedDocs);
    }

    @Override
    public Set<String> getStopWords() throws RemoteException {
        Set<String> stopWords = new HashSet<>();

        if (processedDocs < MIN_DOCS_TO_LEARN) {
            return stopWords; // Ainda não processou documentos suficientes
        }

        for (Map.Entry<String, Integer> entry : docFrequency.entrySet()) {
            double dfRatio = (double) entry.getValue() / processedDocs;
            if (dfRatio > STOPWORD_THRESHOLD) {
                stopWords.add(entry.getKey());
            }
        }
        System.out.println(stopWords);
        return stopWords;
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
