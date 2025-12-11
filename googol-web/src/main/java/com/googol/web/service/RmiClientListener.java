package com.googol.web.service;

import common.IClientCallback; // Importar a interface fornecida
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RmiClientListener extends UnicastRemoteObject implements IClientCallback {

    public RmiClientListener() throws RemoteException {
        super();
    }

    @Override
    public void onStatisticsUpdated(String statsOutput) throws RemoteException {
        System.out.println("RMI Recebido (Stats): " + statsOutput);

        // Envia para o WebSocket para todos os clientes verem
        // (Certifique-se que a classe StatsWebSocket existe como definido anteriormente)
        StatsWebSocket.broadcast(statsOutput);
    }
}