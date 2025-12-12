package com.googol.web.service;

import gateway.IGateway;
import common.UrlMetadata;
import common.IClientCallback; // <--- Importante: usar a interface do common
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

@Service
public class GoogolService {

    private IGateway gateway;

    private adaptivestopwords.IAdaptiveStopWords stopWordsService;

    public GoogolService() {
    }

    @PostConstruct // Poderia estar no construtor mas é boa prática do spring
    public void init() {
        connectToGateway();
    }

    /**
     * Conecta à gateway e junta-se à lista de subscribers para receber estatisticas em tempo real
     */
    private void connectToGateway() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            this.gateway = (IGateway) registry.lookup("Gateway");
            this.stopWordsService = (adaptivestopwords.IAdaptiveStopWords) registry.lookup("AdaptiveStopWords");
            System.out.println("--- Conectado à Gateway RMI ---");

            RmiClientListener myListener = new RmiClientListener();

            // Servidor web fica subscribed à gateway de forma a que receba informação das estatísticas em tempo real
            // Lógica igual à de um cliente RPC/RMI
            gateway.subscribe(myListener);

            System.out.println("--- Subscrito para receber stats via onStatisticsUpdated ---");

        } catch (Exception e) {
            System.err.println("Erro na conexão RMI: " + e.getMessage());
            this.gateway = null;
        }
    }

    // --- Métodos de Pesquisa ---

    /**
     * Devolve uma lista dos termos a pesquisar e devolve os resultados da pesquisa
     * @param query
     * @return resultados da pesquisa
     */
    public Map<String, UrlMetadata> search(String query) {
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) return new HashMap<>();

            // ArrayList de termos de pesquisa independentes para que possamos retirar as Stop Words
            List<String> terms = new ArrayList<>(Arrays.asList(query.trim().split("\\s+")));

            // [SERVIÇO DE STOP WORDS] - Bloquear a pesquisa de termos que sejam Stop Words
            if (stopWordsService != null) {
                try {
                    Set<String> stopWords = new HashSet<>(stopWordsService.getStopWords());

                    System.out.println(stopWords);

                    terms.removeIf(term -> stopWords.contains(term.toLowerCase()));

                    if (terms.isEmpty()) {
                        return new HashMap<>();
                    }
                } catch (RemoteException e) {
                    System.err.println("Aviso: Não foi possível aceder às stop words. Prosseguindo sem filtro.");
                }
            }
            return gateway.search(terms);
        } catch (Exception e) {
            e.printStackTrace();
            gateway = null;
            return new HashMap<>();
        }
    }

    /**
     * Envia para a queue o URL passado como parâmetro para ser indexado
     * @param url
     * @return boolean que indica sucesso da operação
     */
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

    /**
     * Devolve os URLs que apontam para um URL passado como parâmetro
     * @param url
     * @return incomingLinks
     */
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