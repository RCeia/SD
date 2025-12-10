package com.googol.web.multicast;

import barrel.IBarrel;
import com.googol.web.common.PageData;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ReliableMulticast {

    private final int maxRetries;
    private final int ackTimeoutMs;
    private final ExecutorService executor;
    private final int backoffFactor; // Backoff exponencial

    public ReliableMulticast(int maxRetries, int ackTimeoutMs, int maxThreads, int backoffFactor) {
        this.maxRetries = maxRetries;
        this.ackTimeoutMs = ackTimeoutMs;
        this.executor = Executors.newFixedThreadPool(maxThreads); // Controle de threads
        this.backoffFactor = backoffFactor;
    }

    public List<IBarrel> multicastToBarrels(List<IBarrel> barrels, PageData data) {
        if (barrels == null || barrels.isEmpty()) {
            System.err.println("[Multicast] Nenhum barrel disponível!");
            return new ArrayList<>();
        }

        List<IBarrel> pending = new ArrayList<>(barrels);
        int attempt = 0;

        while (!pending.isEmpty() && attempt < maxRetries) {
            attempt++;
            System.out.println("[Multicast] Tentativa " + attempt + " de envio para " + pending.size() + " barrels...");

            List<Future<Boolean>> results = new ArrayList<>();
            for (IBarrel barrel : pending) {
                results.add(executor.submit(() -> {
                    try {
                        barrel.storePage(data);
                        return true;
                    } catch (RemoteException e) {
                        System.err.println("[Multicast] Falha no barrel: " + e.getMessage());
                        return false;
                    }
                }));
            }

            List<IBarrel> failed = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                try {
                    // Obtenha o resultado com o tempo de espera configurado
                    if (!results.get(i).get(ackTimeoutMs, TimeUnit.MILLISECONDS)) {
                        failed.add(pending.get(i));
                    }
                } catch (InterruptedException | TimeoutException e) {
                    // Adiciona falha por timeout ou interrupção
                    System.err.println("[Multicast] Timeout ou interrupção para o barrel: " + pending.get(i));
                    failed.add(pending.get(i));
                } catch (Exception e) {
                    // Adiciona qualquer outra exceção inesperada
                    System.err.println("[Multicast] Erro desconhecido no barrel: " + e.getMessage());
                    failed.add(pending.get(i));
                }
            }

            // Backoff exponencial no tempo de espera em cada tentativa
            if (!failed.isEmpty()) {
                try {
                    long backoffTime = (long) Math.pow(backoffFactor, attempt) * ackTimeoutMs;
                    System.out.println("[Multicast] Aguardando " + backoffTime + "ms antes de nova tentativa.");
                    Thread.sleep(backoffTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();  // Restaura a interrupção
                    System.err.println("[Multicast] A thread foi interrompida durante o backoff.");
                }
            }

            pending = failed;
        }

        if (!pending.isEmpty()) {
            System.err.println("[Multicast] Nem todos os barrels confirmaram após " + maxRetries + " tentativas: " + pending.size() + " falharam.");
        } else {
            System.out.println("[Multicast] Todos os barrels confirmaram com sucesso.");
        }

        // Devolve a lista dos barrels que falharam definitivamente
        return pending;
    }
}
