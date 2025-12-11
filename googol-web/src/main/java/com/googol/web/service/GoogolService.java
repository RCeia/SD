package com.googol.web.service;

import gateway.IGateway;
import common.UrlMetadata;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

@Service
public class GoogolService {

    private IGateway gateway;

    @PostConstruct
    public void init() {
        connectToGateway();
    }

    private void connectToGateway() {
        try {
            // Tenta conectar ao RMI na porta 1099
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            this.gateway = (IGateway) registry.lookup("Gateway");
            System.out.println("--- Conectado à Gateway RMI ---");
        } catch (Exception e) {
            System.err.println("Erro ao conectar à Gateway: " + e.getMessage());
        }
    }

    // 1. Funcionalidade de Pesquisa
    public Map<String, UrlMetadata> search(String query) {
        try {
            if (gateway == null) connectToGateway();
            List<String> terms = Arrays.asList(query.trim().split("\\s+"));
            return gateway.search(terms);
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    // 2. Funcionalidade de Indexar URL
    public boolean indexURL(String url) {
        try {
            if (gateway == null) connectToGateway();
            gateway.indexURL(url);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 3. Funcionalidade de Links de Entrada
    public List<String> getIncomingLinks(String url) {
        try {
            if (gateway == null) connectToGateway();
            return gateway.getIncomingLinks(url);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}