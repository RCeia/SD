package com.googol.web.service;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

// Define o URL do endpoint tal como na página 9 do PDF
@Component
@ServerEndpoint(value = "/stats")
public class StatsWebSocket {

    // Lista estática thread-safe para guardar todas as sessões ativas (PDF pág. 17 )
    private static final Set<StatsWebSocket> connections = new CopyOnWriteArraySet<>();

    private Session session;

    public StatsWebSocket() {
        // Construtor vazio
    }

    // Chamado quando um browser abre a conexão (PDF pág. 18 [cite: 229])
    @OnOpen
    public void start(Session session) {
        this.session = session;
        connections.add(this); // Adiciona este cliente à lista
        System.out.println("Nova conexão WebSocket aberta: " + session.getId());
    }

    // Chamado quando a conexão fecha (PDF pág. 18 [cite: 236])
    @OnClose
    public void end() {
        connections.remove(this); // Remove da lista
        System.out.println("Conexão WebSocket fechada.");
    }

    @OnMessage
    public void incoming(String message) {
        // Não precisamos de receber nada do cliente para este caso,
        // mas o método pode existir se necessário (PDF pág. 10 [cite: 68]).
    }

    @OnError
    public void onError(Throwable t) throws Throwable {
        // Tratamento de erros
        t.printStackTrace();
    }

    // Método estático para enviar mensagens a todos os clientes conectados
    // Segue a lógica de 'broadcast' da página 19 do PDF [cite: 252]
    public static void broadcast(String msg) {
        for (StatsWebSocket client : connections) {
            try {
                synchronized (client) {
                    // Envia o texto para o cliente (PDF pág. 11 [cite: 96])
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