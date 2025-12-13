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

/**
 * Serviço Spring que atua como ponte entre a aplicação Web e o sistema Googol (RMI).
 * <p>
 * Responsável por:
 * <ul>
 * <li>Manter a ligação RMI com o Gateway.</li>
 * <li>Encaminhar pesquisas e pedidos de indexação.</li>
 * <li>Gerir a subscrição para receber estatísticas em tempo real.</li>
 * <li>Aplicar filtros de Stop Words antes de enviar as pesquisas.</li>
 * </ul>
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
@Service
public class GoogolService {

    /**
     * Referência para o objeto remoto Gateway.
     */
    private IGateway gateway;

    /**
     * Referência para o serviço remoto de identificação de Stop Words.
     */
    private adaptivestopwords.IAdaptiveStopWords stopWordsService;

    /**
     * Construtor padrão do serviço.
     */
    public GoogolService() {
    }

    /**
     * Inicializa a conexão com o Gateway RMI após a construção do Bean Spring.
     * Anotado com {@code @PostConstruct} para garantir execução automática.
     */
    @PostConstruct // Poderia estar no construtor mas é boa prática do spring
    public void init() {
        connectToGateway();
    }

    /**
     * Estabelece a ligação RMI com o Gateway e subscreve-se para receber atualizações.
     * <p>
     * Cria uma instância de {@code RmiClientListener} e regista-a no Gateway via
     * {@code subscribe()}, permitindo que o servidor web receba notificações (push)
     * sobre o estado do sistema (estatísticas).
     * </p>
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
     * Realiza uma pesquisa no sistema Googol com suporte a paginação e filtragem de Stop Words.
     * <p>
     * O método:
     * 1. Divide a query em termos.
     * 2. Remove termos identificados como Stop Words pelo serviço remoto.
     * 3. Adiciona a tag de paginação {@code [PAGE:X]} ao primeiro termo da lista, se necessário.
     * 4. Invoca o método de pesquisa do Gateway.
     * </p>
     *
     * @param query A string contendo os termos a pesquisar.
     * @param page O número da página de resultados pretendida (>= 1).
     * @return Um mapa contendo os URLs encontrados e os seus metadados. Retorna vazio em caso de erro.
     */
    public Map<String, UrlMetadata> search(String query, int page) { // <--- 1. ADICIONAR int page
        try {
            if (gateway == null) connectToGateway();
            if (gateway == null) return new HashMap<>();

            // ArrayList de termos de pesquisa independentes
            List<String> terms = new ArrayList<>(Arrays.asList(query.trim().split("\\s+")));

            // [SERVIÇO DE STOP WORDS] - Bloquear a pesquisa de termos que sejam Stop Words
            if (stopWordsService != null) {
                try {
                    Set<String> stopWords = new HashSet<>(stopWordsService.getStopWords());

                    // Remove stop words (ex: "o", "a", "de")
                    terms.removeIf(term -> stopWords.contains(term.toLowerCase()));

                    if (terms.isEmpty()) {
                        return new HashMap<>();
                    }
                } catch (RemoteException e) {
                    System.err.println("Aviso: Não foi possível aceder às stop words.");
                }
            }

            // --- 2. NOVA LÓGICA DE PAGINAÇÃO AQUI ---
            // Se a página for maior que 1, colamos a etiqueta no primeiro termo da lista.
            // Exemplo: ["futebol", "benfica"] -> ["futebol[PAGE:2]", "benfica"]
            // O Barrel vai ler isto e saber que queres a página 2.
            if (page > 1 && !terms.isEmpty()) {
                String firstTerm = terms.get(0);
                terms.set(0, firstTerm + "[PAGE:" + page + "]");
            }

            return gateway.search(terms);

        } catch (Exception e) {
            e.printStackTrace();
            gateway = null;
            return new HashMap<>();
        }
    }

    /**
     * Envia um URL para a fila de indexação do sistema.
     *
     * @param url O URL a ser indexado.
     * @return {@code true} se a operação for bem sucedida, {@code false} caso contrário.
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
     * Obtém a lista de URLs que apontam para um determinado URL (Backlinks).
     *
     * @param url O URL alvo.
     * @return Uma lista de strings com os URLs de origem.
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