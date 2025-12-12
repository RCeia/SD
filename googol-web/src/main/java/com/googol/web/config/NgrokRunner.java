package com.googol.web.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;

@Component
public class NgrokRunner {

    @EventListener(ApplicationReadyEvent.class)
    public void autoStartNgrok() {
        System.out.println("=================================================");
        System.out.println(">>> A PREPARAR NGROK... ");

        try {
            // 1. Descobrir onde está a correr o projeto
            String projectDir = System.getProperty("user.dir");
            String ngrokPath = projectDir + File.separator + "ngrok.exe";

            // 2. Verificar se o ficheiro existe mesmo
            File ngrokFile = new File(ngrokPath);
            if (!ngrokFile.exists()) {
                System.err.println("❌ ERRO: O ficheiro ngrok.exe não foi encontrado em:");
                System.err.println("   -> " + ngrokPath);
                System.err.println("   Mova o ngrok.exe para a pasta raiz do projeto (ao lado do pom.xml)!");
                return;
            }

            System.out.println(">>> A INICIAR NGROK DE: " + ngrokPath);
            System.out.println("=================================================");

            // 3. Montar o comando usando o caminho absoluto
            // "start" abre uma nova janela. As aspas vazias "" são para o título da janela (truque do CMD)
            String command = "cmd.exe /c start \"Googol Tunnel\" \"" + ngrokPath + "\" http https://localhost:8443";

            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            processBuilder.start();

        } catch (IOException e) {
            System.err.println("❌ Falha crítica ao iniciar ngrok: " + e.getMessage());
        }
    }
}