package com.googol.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação Web (Spring Boot) do sistema Googol.
 * <p>
 * Esta classe é responsável por inicializar o contexto do Spring, arrancar com o
 * servidor embutido (Tomcat) e realizar a configuração automática dos componentes
 * web (Controllers, Services, etc.) definidos neste pacote e sub-pacotes.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
@SpringBootApplication
public class GoogolWebApplication {

    /**
     * Método principal que inicia a execução da aplicação web.
     * <p>
     * Invoca o {@code SpringApplication.run} para configurar o ambiente,
     * criar o contexto da aplicação e expor os endpoints HTTP.
     * </p>
     *
     * @param args Argumentos de linha de comando passados para a aplicação (opcionais).
     */
    public static void main(String[] args) {
        SpringApplication.run(GoogolWebApplication.class, args);
    }

}