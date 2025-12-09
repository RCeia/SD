package com.googol.web.service;

import gateway.IGateway;
import common.PageData; // Classe de dados (DTO)
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class GoogolService {

    // Configuração RMI
    private IGateway gateway;
    private final String RMI_HOST = "localhost";
    private final int RMI_PORT = 1099;
    private final String RMI_SERVICE = "Gateway"; // Nome exato usado no registry

    @PostConstruct
    public void init() {
        System.out.println("[GoogolService] A iniciar serviço web...");
        connectToRMI();
    }

    public boolean connectToRMI() {
        try {
            // Constrói o URL RMI: rmi://localhost:1099/Gateway
            String rmiUrl = String.format("rmi://%s:%d/%s", RMI_HOST, RMI_PORT, RMI_SERVICE);
            this.gateway = (IGateway) Naming.lookup(rmiUrl);
            System.out.println("[RMI] Ligado com sucesso a: " + rmiUrl);
            return true;
        } catch (Exception e) {
            System.err.println("[RMI] Falha na conexão: " + e.getMessage());
            return false;
        }
    }

    /**
     * Realiza a pesquisa e converte o resultado do formato "Map" (Meta 1)
     * para "List<PageData>" (Meta 2 - MVC).
     */
    public List<PageData> search(String query) {
        if (this.gateway == null && !connectToRMI()) {
            return Collections.emptyList();
        }

        try {
            // 1. Preparar termos
            List<String> terms = List.of(query.trim().split("\\s+"));

            // 2. Chamada RMI (Recebe Map<URL, "Título\nCitação">)
            Map<String, String> rawResults = this.gateway.search(terms);

            if (rawResults == null || rawResults.isEmpty()) {
                return Collections.emptyList();
            }

            // 3. Converter para objetos PageData
            List<PageData> cleanResults = new ArrayList<>();

            for (Map.Entry<String, String> entry : rawResults.entrySet()) {
                String url = entry.getKey();
                String rawValue = entry.getValue();

                String title = "Sem Título";
                String citation = "Sem descrição.";

                // Lógica de split para separar Título de Citação
                if (rawValue != null) {
                    // Divide na primeira quebra de linha encontrada
                    String[] parts = rawValue.split("\n", 2);

                    if (parts.length > 0) title = parts[0];
                    if (parts.length > 1) citation = parts[1];
                }

                // Cria o objeto para a View (Thymeleaf)
                // Assumindo o construtor: PageData(url, title, citation)
                // Se a tua PageData ainda pedir listas, passa null nos últimos argumentos.
                cleanResults.add(new PageData(url, title, citation));
            }

            return cleanResults;

        } catch (RemoteException e) {
            System.err.println("[RMI] Erro na pesquisa: " + e.getMessage());
            this.gateway = null; // Força reconexão futura
            return Collections.emptyList();
        }
    }

    // Método extra para a funcionalidade de "Indexar URL" (Opção 1 do teu menu antigo)
    public String indexURL(String url) {
        if (this.gateway == null && !connectToRMI()) return "Servidor Offline";
        try {
            return this.gateway.indexURL(url);
        } catch (RemoteException e) {
            return "Erro: " + e.getMessage();
        }
    }
}