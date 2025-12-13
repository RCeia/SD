package multicast;

import barrel.IBarrel;
import common.PageData;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Classe responsável pelo envio de dados em modo multicast fiável para múltiplos Barrels.
 * <p>
 * Esta classe gere o envio paralelo de dados, lidando com retransmissões automáticas,
 * timeouts e espera exponencial (backoff) em caso de falhas temporárias.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class ReliableMulticast {

    /**
     * Número máximo de tentativas de reenvio em caso de falha.
     */
    private final int maxRetries;

    /**
     * Tempo limite (em milissegundos) para aguardar a confirmação (ACK) de um Barrel.
     */
    private final int ackTimeoutMs;

    /**
     * Pool de threads para gestão de envios concorrentes.
     */
    private final ExecutorService executor;

    /**
     * Fator base para o cálculo do tempo de espera exponencial entre tentativas (Backoff).
     */
    private final int backoffFactor; // Backoff exponencial

    /**
     * Construtor da classe ReliableMulticast.
     * Inicializa as configurações de retentativa, timeout e gestão de threads.
     *
     * @param maxRetries Número máximo de tentativas de envio por Barrel.
     * @param ackTimeoutMs Tempo limite de espera por resposta em milissegundos.
     * @param maxThreads Número máximo de threads simultâneas para envio.
     * @param backoffFactor Fator de multiplicação para o tempo de espera entre falhas.
     */
    public ReliableMulticast(int maxRetries, int ackTimeoutMs, int maxThreads, int backoffFactor) {
        this.maxRetries = maxRetries;
        this.ackTimeoutMs = ackTimeoutMs;
        this.executor = Executors.newFixedThreadPool(maxThreads); // Controle de threads
        this.backoffFactor = backoffFactor;
    }

    /**
     * Envia um objeto {@code PageData} para uma lista de Barrels.
     * <p>
     * O método tenta enviar os dados para todos os Barrels fornecidos. Se algum falhar
     * ou exceder o tempo limite, o sistema aguarda um tempo (baseado no backoff factor)
     * e tenta novamente até atingir o número máximo de tentativas (`maxRetries`).
     * </p>
     *
     * @param barrels Lista de interfaces remotas dos Barrels de destino.
     * @param data O objeto contendo os dados da página a ser armazenada.
     * @return Uma lista contendo os Barrels que falharam definitivamente após todas as tentativas.
     * Retorna uma lista vazia se todos confirmarem o recebimento com sucesso.
     */
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