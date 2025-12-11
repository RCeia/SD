package com.googol.web.service;

import gateway.IGateway;
import common.UrlMetadata;
import common.IClientCallback; // <--- Importante: usar a interface do common
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

@Service
public class GoogolService {

    private IGateway gateway;

    public GoogolService() {
    }

    @PostConstruct
    public void init() {
        connectToGateway();
    }

    private void connectToGateway() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            this.gateway = (IGateway) registry.lookup("Gateway");
            System.out.println("--- Conectado à Gateway RMI ---");

            // --- CORREÇÃO AQUI ---

            // 1. Instanciamos o listener
            RmiClientListener myListener = new RmiClientListener();

            // 2. Passamos APENAS o listener (sem a String "SpringBootWeb")
            // A interface IGateway espera: subscribe(IClientCallback callback)
            gateway.subscribe(myListener);

            System.out.println("--- Subscrito para receber stats via onStatisticsUpdated ---");

        } catch (Exception e) {
            System.err.println("Erro na conexão RMI: " + e.getMessage());
            this.gateway = null;
        }
    }

    // --- Métodos de Pesquisa ---

    public Map<String, UrlMetadata> search(String query) {
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) return new HashMap<>();

            List<String> terms = Arrays.asList(query.trim().split("\\s+"));
            return gateway.search(terms);
        } catch (Exception e) {
            e.printStackTrace();
            gateway = null;
            return new HashMap<>();
        }
    }

    public boolean indexURL(String url) {
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) return false;

            gateway.indexURL(url);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            gateway = null;
            return false;
        }
    }

    public List<String> getIncomingLinks(String url) {
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) return new ArrayList<>();

            return gateway.getIncomingLinks(url);
        } catch (Exception e) {
            e.printStackTrace();
            gateway = null;
            return new ArrayList<>();
        }
    }
}