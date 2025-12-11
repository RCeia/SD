package com.googol.web.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class HackerNewsService {

    private final String BASE_URL = "https://hacker-news.firebaseio.com/v0";
    private final RestTemplate restTemplate;

    public HackerNewsService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Procura nas Top Stories do Hacker News por artigos que contenham o termo.
     * @param query Termo de pesquisa
     * @return Lista de URLs encontrados
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