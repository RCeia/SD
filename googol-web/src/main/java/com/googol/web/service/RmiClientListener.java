package com.googol.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.IClientCallback;
import common.SystemStatistics; // Importar a sua classe
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RmiClientListener extends UnicastRemoteObject implements IClientCallback {

    private final ObjectMapper objectMapper;

    public RmiClientListener() throws RemoteException {
        super();
        this.objectMapper = new ObjectMapper();
    }

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