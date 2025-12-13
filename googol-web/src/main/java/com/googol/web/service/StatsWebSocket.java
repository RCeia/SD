package com.googol.web.service;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Endpoint WebSocket para difusão de estatísticas em tempo real para o browser.
 * <p>
 * Mapeado em {@code /stats}, gere as conexões dos clientes e distribui as mensagens
 * JSON recebidas do RMI para a interface web (Dashboard).
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
@Component
@ServerEndpoint(value = "/stats")
public class StatsWebSocket {

    /**
     * Conjunto thread-safe de sessões WebSocket ativas.
     */
    private static final Set<StatsWebSocket> connections = new CopyOnWriteArraySet<>();

    /**
     * Cache da última mensagem de estado recebida.
     * Útil para enviar dados imediatos a novos clientes que se conectam.
     */
    private static volatile String currentSystemState = null;

    /**
     * Sessão individual de um cliente conectado.
     */
    private Session session;

    /**
     * Construtor padrão.
     */
    public StatsWebSocket() {
    }

    /**
     * Chamado quando uma nova conexão WebSocket é aberta.
     * <p>
     * Adiciona a conexão à lista e envia imediatamente o último estado conhecido do sistema.
     * </p>
     *
     * @param session A sessão do cliente.
     */
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

    /**
     * Chamado quando uma conexão WebSocket é fechada.
     */
    @OnClose
    public void end() {
        connections.remove(this);
    }

    /**
     * Chamado quando uma mensagem é recebida do cliente (não utilizado neste contexto unidirecional).
     *
     * @param message A mensagem recebida.
     */
    @OnMessage
    public void incoming(String message) { }

    /**
     * Tratamento de erros na conexão WebSocket.
     *
     * @param t A exceção capturada.
     */
    @OnError
    public void onError(Throwable t) { t.printStackTrace(); }

    /**
     * Envia uma mensagem para todos os clientes conectados.
     * <p>
     * Atualiza também o estado em cache ({@code currentSystemState}).
     * Remove automaticamente clientes desconectados ou com erros de I/O.
     * </p>
     *
     * @param msg A mensagem (JSON) a enviar.
     */
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