package com.googol.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.IClientCallback;
import common.SystemStatistics; // Importar a sua classe
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Implementação do callback RMI para receber estatísticas do Gateway.
 * <p>
 * Esta classe age como um "ouvinte" que recebe objetos {@code SystemStatistics} do RMI,
 * converte-os para JSON e reencaminha-os para os clientes Web via WebSocket.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class RmiClientListener extends UnicastRemoteObject implements IClientCallback {

    /**
     * Mapeador de objetos Jackson para serialização JSON.
     */
    private final ObjectMapper objectMapper;

    /**
     * Construtor da classe listener.
     *
     * @throws RemoteException Se ocorrer erro na exportação do objeto remoto.
     */
    public RmiClientListener() throws RemoteException {
        super();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Método invocado remotamente pelo Gateway quando as estatísticas são atualizadas.
     * <p>
     * O método realiza:
     * 1. Receção do objeto {@code SystemStatistics}.
     * 2. Conversão para String JSON.
     * 3. Difusão (broadcast) para todos os clientes WebSocket conectados.
     * </p>
     *
     * @param stats O objeto com as estatísticas atualizadas.
     * @throws RemoteException Se ocorrer erro na comunicação RMI.
     */
    @Override
    public void onStatisticsUpdated(SystemStatistics stats) throws RemoteException {
        try {
            // 1. Recebemos o objeto Java 'stats' já populado pelo RMI.
            // 2. Convertemos diretamente para JSON String.
            String jsonOutput = objectMapper.writeValueAsString(stats);

            // Debug opcional
            // System.out.println("JSON gerado: " + jsonOutput);

            // 3. Enviamos para o HTML
            StatsWebSocket.broadcast(jsonOutput);

        } catch (Exception e) {
            System.err.println("Erro ao converter SystemStatistics para JSON: " + e.getMessage());
        }
    }
}