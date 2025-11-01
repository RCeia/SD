package common;

import java.rmi.RemoteException;

/**
 * A generic retry mechanism that can be reused across modules (Client, Gateway, etc.).
 * Supports configurable retry count, delay, and reconnection logic.
 */
public class RetryLogic {

    /**
     * Functional interface representing any remote operation that can fail and throw a RemoteException.
     */
    @FunctionalInterface
    public interface Operation<T> {
        T execute() throws RemoteException;
    }

    /**
     * Executes the given operation with retry logic and optional reconnection.
     *
     * @param retries    Number of retry attempts before failing.
     * @param delay      Delay (in milliseconds) between retries.
     * @param reconnect  Optional reconnection logic to be executed when retries are exhausted.
     * @param operation  The operation to execute.
     * @param <T>        Return type of the operation.
     * @return The result of the operation if successful.
     * @throws RemoteException     If the operation fails permanently.
     * @throws InterruptedException If the retry delay is interrupted.
     */
    public static <T> T executeWithRetry(int retries, long delay, ReconnectAction reconnect, Operation<T> operation)
            throws RemoteException, InterruptedException {

        int attempt = 0;
        while (attempt < retries) {
            try {
                return operation.execute(); // Try the remote operation
            } catch (RemoteException e) {
                attempt++;
                System.err.println("[RetryLogic] Falha ao executar operação (tentativa " + attempt + "/" + retries + "): ");

                if (attempt >= retries) {
                    // Try reconnection if provided
                    if (reconnect != null && reconnect.reconnect()) {
                        System.out.println("[RetryLogic] Reconexão bem-sucedida. Tentando novamente a operação...");
                        return operation.execute();
                    } else {
                        throw new RemoteException("Falha após múltiplas tentativas e reconexão não sucedida.", e);
                    }
                }

                Thread.sleep(delay);
            }
        }
        return null;
    }

    /**
     * Functional interface representing an action to reconnect, returning true if successful.
     */
    @FunctionalInterface
    public interface ReconnectAction {
        boolean reconnect();
    }
}
