package com.googol.web.service;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ServerEndpoint(value = "/stats")
public class StatsWebSocket {

    private static final Set<StatsWebSocket> connections = new CopyOnWriteArraySet<>();

    // Esta variável guarda o "Estado Atual". Se a Gateway disse que há 2 barrels,
    // esta variável guarda essa informação para mostrar a quem entrar no site agora.
    private static volatile String currentSystemState = null;

    private Session session;

    public StatsWebSocket() {
    }

    @OnOpen
    public void start(Session session) {
        this.session = session;
        connections.add(this);

        // ENVIO IMEDIATO DO ESTADO ATUAL
        // Isto garante que vê os Barrels assim que abre a página
        if (currentSystemState != null) {
            try {
                session.getBasicRemote().sendText(currentSystemState);
            } catch (IOException e) {
                // ignorar
            }
        }
    }

    @OnClose
    public void end() {
        connections.remove(this);
    }

    @OnMessage
    public void incoming(String message) { }

    @OnError
    public void onError(Throwable t) { t.printStackTrace(); }

    public static void broadcast(String msg) {
        // Atualizamos o estado atual sempre que a Gateway manda novidades
        currentSystemState = msg;

        for (StatsWebSocket client : connections) {
            try {
                synchronized (client) {
                    client.session.getBasicRemote().sendText(msg);
                }
            } catch (IOException e) {
                connections.remove(client);
                try { client.session.close(); } catch (IOException i) {}
            }
        }
    }
}