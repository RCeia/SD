package common;

import java.rmi.RemoteException;

/**
 * Classe utilitária que implementa um mecanismo genérico de re-tentativas (Retry Logic).
 * <p>
 * Pode ser reutilizada por vários módulos (Cliente, Gateway, Downloader) para lidar
 * com falhas transitórias de rede ou RMI, suportando re-conexão opcional.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class RetryLogic {

    /**
     * Interface funcional que representa qualquer operação remota que pode falhar.
     *
     * @param <T> O tipo de retorno da operação.
     */
    @FunctionalInterface
    public interface Operation<T> {
        /**
         * Executa a operação que pode lançar uma exceção remota.
         *
         * @return O resultado da operação.
         * @throws RemoteException Se a operação falhar.
         */
        T execute() throws RemoteException;
    }

    /**
     * Executa uma operação com lógica de re-tentativa e reconexão opcional.
     *
     * @param retries Número máximo de tentativas antes de desistir.
     * @param delay Tempo de espera (em milissegundos) entre tentativas.
     * @param reconnect Ação de reconexão a ser executada se as tentativas falharem (pode ser null).
     * @param operation A operação principal a ser executada.
     * @param <T> O tipo de retorno da operação.
     * @return O resultado da operação se for bem-sucedida.
     * @throws RemoteException Se a operação falhar permanentemente após todas as tentativas.
     */
    public static <T> T executeWithRetry(int retries, long delay, ReconnectAction reconnect, Operation<T> operation)
            throws RemoteException {

        int attempt = 0;
        while (attempt < retries) {
            try {
                return operation.execute();
            } catch (RemoteException e) {
                attempt++;
                System.err.println("[RetryLogic] Falha ao executar operação (tentativa "
                        + attempt + "/" + retries + "): " + e.getMessage());

                if (attempt >= retries) {
                    // Tentativa de reconexão se existir ação
                    if (reconnect != null && reconnect.reconnect()) {
                        System.out.println("[RetryLogic] Reconexão bem-sucedida. Tentando novamente a operação...");
                        return operation.execute();
                    } else {
                        throw new RemoteException("Falha após múltiplas tentativas e reconexão não sucedida.", e);
                    }
                }

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // preserva estado da thread
                    throw new RemoteException("Thread interrompida durante retries", ie);
                }
            }
        }
        return null;
    }

    /**
     * Interface funcional que representa uma ação de reconexão.
     */
    @FunctionalInterface
    public interface ReconnectAction {
        /**
         * Executa a lógica de reconexão.
         *
         * @return true se a reconexão for bem-sucedida, false caso contrário.
         */
        boolean reconnect();
    }
}