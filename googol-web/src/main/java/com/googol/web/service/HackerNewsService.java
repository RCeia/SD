package com.googol.web.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serviço de integração com a API pública do Hacker News.
 * <p>
 * Permite pesquisar nas "Top Stories" atuais do Hacker News por artigos que
 * contenham determinados termos no título, retornando os seus URLs para indexação.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
@Service
public class HackerNewsService {

    /**
     * URL base da API do Hacker News.
     */
    private final String BASE_URL = "https://hacker-news.firebaseio.com/v0";

    /**
     * Cliente REST para efetuar chamadas HTTP.
     */
    private final RestTemplate restTemplate;

    /**
     * Construtor do serviço.
     */
    public HackerNewsService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Pesquisa nas Top Stories do Hacker News por artigos que correspondam ao termo.
     * <p>
     * O processo envolve:
     * 1. Obter a lista de IDs das "topstories".
     * 2. Iterar sobre os primeiros 30 IDs.
     * 3. Obter os detalhes de cada história (título e URL).
     * 4. Filtrar as histórias cujo título contenha o termo pesquisado.
     * </p>
     *
     * @param query Termo de pesquisa.
     * @return Lista de URLs dos artigos encontrados.
     */
    public List<String> searchAndGetUrls(String query) {
        List<String> foundUrls = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        try {
            // 1. Obter os IDs das Top Stories (retorna uma lista de Inteiros: [1234, 5678, ...])
            ResponseEntity<List<Integer>> response = restTemplate.exchange(
                    BASE_URL + "/topstories.json",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Integer>>() {}
            );

            List<Integer> topStoryIds = response.getBody();

            if (topStoryIds != null) {
                // 2. Iterar sobre as primeiras 30 histórias (limite para não bloquear o servidor)
                // A API do HN obriga a fazer um pedido por cada história, o que é lento.
                int limit = Math.min(topStoryIds.size(), 30);

                for (int i = 0; i < limit; i++) {
                    Integer id = topStoryIds.get(i);
                    String itemUrl = BASE_URL + "/item/" + id + ".json";

                    // 3. Obter detalhes da história
                    Map<String, Object> story = restTemplate.getForObject(itemUrl, Map.class);

                    if (story != null && story.containsKey("url") && story.containsKey("title")) {
                        String title = ((String) story.get("title")).toLowerCase();
                        String url = (String) story.get("url");

                        // 4. Se o título contiver o termo pesquisado, guardamos o URL
                        if (title.contains(lowerQuery)) {
                            foundUrls.add(url);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao contactar Hacker News: " + e.getMessage());
        }

        return foundUrls;
    }
}