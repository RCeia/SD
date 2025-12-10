package com.googol.web.service;

import common.UrlMetadata;
import gateway.IGateway;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.util.*;

@Service
public class GoogolService {

    // --- Configurações ---
    private static final int RETRY_LIMIT = 3;
    private static final long RETRY_DELAY = 1000;
    private static final String[] GATEWAY_HOSTS = {"localhost", "backupGateway"};

    private IGateway gateway;

    // --- Inicialização Automática ---
    @PostConstruct
    public void init() {
        System.out.println("[GoogolService] A iniciar serviço...");

        // 1. Tenta conectar
        boolean connected = connectToGateway();

        // 2. Se conectou, EXECUTA O TESTE IMEDIATAMENTE
        if (connected) {
            runTestSearch();
        }
    }

    // --- MÉTODO DE TESTE RÁPIDO ---
    private void runTestSearch() {
        System.out.println("\n========================================");
        System.out.println(">>> A EXECUTAR TESTE: Pesquisa 'wikipedia'");
        System.out.println("========================================");

        // Faz a pesquisa real usando a tua Gateway
        Map<String, UrlMetadata> results = search("wikipedia");

        if (results.isEmpty()) {
            System.out.println("Resultado: Nenhum resultado encontrado (ou a Gateway devolveu vazio).");
        } else {
            System.out.println("Resultado: Encontrei " + results.size() + " páginas!");
            results.forEach((url, meta) -> {
                System.out.println("  - URL: " + url);
                System.out.println("    Título: " + (meta != null ? meta.getTitle() : "Sem Título"));
            });
        }
        System.out.println("========================================\n");
    }

    // --- Lógica de Conexão ---
    private boolean connectToGateway() {
        for (int attempt = 0; attempt < RETRY_LIMIT; attempt++) {
            for (String host : GATEWAY_HOSTS) {
                try {
                    Registry registry = LocateRegistry.getRegistry(host, 1099);
                    this.gateway = (IGateway) registry.lookup("Gateway");
                    System.out.println("[GoogolService] Conectado com sucesso à Gateway em " + host);
                    return true;
                } catch (Exception e) {
                    System.err.println("[GoogolService] Falha ao conectar em " + host + ": " + e.getMessage());
                }
                try { Thread.sleep(RETRY_DELAY); } catch (InterruptedException ignored) {}
            }
        }
        System.err.println("[GoogolService] ERRO CRÍTICO: Não foi possível conectar a nenhuma Gateway.");
        return false;
    }

    private void reconnect() {
        System.out.println("[GoogolService] Conexão perdida. A tentar reconectar...");
        connectToGateway();
    }

    // --- Funcionalidades do Serviço ---

    public String indexURL(String url) {
        return executeWithRetry(() -> {
            gateway.indexURL(url);
            return "URL enviado para indexação: " + url;
        }, "Erro ao indexar URL");
    }

    public Map<String, UrlMetadata> search(String query) {
        return executeWithRetry(() -> {
            List<String> terms = Arrays.asList(query.trim().split("\\s+"));
            Map<String, UrlMetadata> results = gateway.search(terms);
            return (results != null) ? results : new HashMap<>();
        }, new HashMap<>());
    }

    public List<String> getIncomingLinks(String url) {
        return executeWithRetry(() -> {
            List<String> links = gateway.getIncomingLinks(url);
            return (links != null) ? links : new ArrayList<>();
        }, new ArrayList<>());
    }

    public String getSystemStats() {
        return executeWithRetry(() -> gateway.getSystemStats(), "Serviço indisponível.");
    }

    // --- Helper Retry ---
    private <T> T executeWithRetry(RemoteOperation<T> operation, T fallbackValue) {
        for (int i = 0; i < RETRY_LIMIT; i++) {
            try {
                if (gateway == null) connectToGateway();
                if (gateway != null) return operation.execute();
            } catch (RemoteException e) {
                System.err.println("[GoogolService] Erro RMI: " + e.getMessage());
                reconnect();
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
        return fallbackValue;
    }

    @FunctionalInterface
    interface RemoteOperation<T> {
        T execute() throws RemoteException;
    }
}