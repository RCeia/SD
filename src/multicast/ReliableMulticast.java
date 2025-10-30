package multicast;

import barrel.IBarrel;
import common.PageData;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ReliableMulticast {

    private final int maxRetries;
    private final int ackTimeoutMs;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ReliableMulticast(int maxRetries, int ackTimeoutMs) {
        this.maxRetries = maxRetries;
        this.ackTimeoutMs = ackTimeoutMs;
    }

    public List<IBarrel> multicastToBarrels(List<IBarrel> barrels, PageData data) {
        if (barrels == null || barrels.isEmpty()) {
            System.err.println("[MulticastManager] Nenhum barrel disponível!");
            return new ArrayList<>();
        }

        List<IBarrel> pending = new ArrayList<>(barrels);
        int attempt = 0;

        while (!pending.isEmpty() && attempt < maxRetries) {
            attempt++;
            System.out.println("[MulticastManager] Tentativa " + attempt +
                    " de envio para " + pending.size() + " barrels...");

            List<Future<Boolean>> results = new ArrayList<>();
            for (IBarrel barrel : pending) {
                results.add(executor.submit(() -> {
                    try {
                        barrel.storePage(data);
                        return true;
                    } catch (RemoteException e) {
                        System.err.println("[MulticastManager] Falha no barrel: " + e.getMessage());
                        return false;
                    }
                }));
            }

            List<IBarrel> failed = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                try {
                    if (!results.get(i).get(ackTimeoutMs, TimeUnit.MILLISECONDS))
                        failed.add(pending.get(i));
                } catch (Exception e) {
                    failed.add(pending.get(i));
                }
            }

            pending = failed;
        }

        if (!pending.isEmpty()) {
            System.err.println("[MulticastManager] Nem todos os barrels confirmaram após "
                    + maxRetries + " tentativas: " + pending.size() + " falharam.");
        } else {
            System.out.println("[MulticastManager] Todos os barrels confirmaram com sucesso.");
        }

        // devolve a lista dos que falharam definitivamente
        return pending;
    }
}
