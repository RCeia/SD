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

    // NOVO: Cache da última estatística recebida
    private static volatile String lastMessage = null;

    private Session session;

    public StatsWebSocket() {
    }

    @OnOpen
    public void start(Session session) {
        this.session = session;
        connections.add(this);

        // --- A ALTERAÇÃO MÁGICA ---
        // Se já tivermos dados guardados em memória, enviamos
        // imediatamente para este novo utilizador!
        if (lastMessage != null) {
            try {
                session.getBasicRemote().sendText(lastMessage);
            } catch (IOException e) {
                // Ignorar falha no envio inicial
            }
        }
    }

    @OnClose
    public void end() {
        connections.remove(this);
    }

    @OnMessage
    public void incoming(String message) {
        // Não utilizado
    }

    @OnError
    public void onError(Throwable t) {
        t.printStackTrace();
    }

    public static void broadcast(String msg) {
        // NOVO: Atualiza a cache sempre que houver novos dados
        lastMessage = msg;

        for (StatsWebSocket client : connections) {
            try {
                synchronized (client) {
                    client.session.getBasicRemote().sendText(msg);
                }
            } catch (IOException e) {
                connections.remove(client);
                try {
                    client.session.close();
                } catch (IOException ioException) {
                    // Ignorar
                }
            }
        }
    }
}