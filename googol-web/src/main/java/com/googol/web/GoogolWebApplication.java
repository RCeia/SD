package com.googol.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação Web.
 * A anotação @SpringBootApplication inicia o servidor Tomcat embutido,
 * configura as dependências automaticamente e procura por Controllers e Services
 * neste pacote e nos sub-pacotes para os carregar.
 */
@SpringBootApplication
public class GoogolWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoogolWebApplication.class, args);
    }

}
