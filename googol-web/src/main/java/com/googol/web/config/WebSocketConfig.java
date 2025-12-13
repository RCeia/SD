package com.googol.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * Classe de configuração para suporte a WebSockets no Spring Boot.
 * <p>
 * Esta classe é necessária para ativar a deteção automática de endpoints WebSocket
 * anotados com {@code @ServerEndpoint} quando a aplicação corre num contentor
 * de servlets embutido (como o Tomcat do Spring Boot).
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
@Configuration
public class WebSocketConfig {

    /**
     * Cria e regista o bean {@code ServerEndpointExporter}.
     * <p>
     * Este bean é responsável por varrer o contexto da aplicação à procura de
     * classes anotadas como endpoints de WebSocket e registá-las no servidor subjacente.
     * </p>
     *
     * @return O objeto {@code ServerEndpointExporter} configurado.
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}