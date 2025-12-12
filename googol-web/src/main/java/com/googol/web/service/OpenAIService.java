package com.googol.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    public OpenAIService() {
        this.restTemplate = new RestTemplate();
    }

    public String generateSummary(String query) {
        // Se não houver chave configurada, não faz nada (evita erros)
        if (apiKey == null || apiKey.contains("TUA_CHAVE") || apiKey.isEmpty()) {
            return null;
        }

        try {
            // 1. Configurar Cabeçalhos (Headers)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 2. Construir o Corpo do Pedido (JSON)
            // Estrutura: { "model": "gpt-3.5-turbo", "messages": [...] }
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            // Prompt: Pede uma análise contextualizada curta sobre a pesquisa
            userMessage.put("content", "Forneça uma explicação muito breve e contextualizada (máximo 2 frases) sobre o termo de pesquisa: " + query);
            messages.add(userMessage);

            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 100); // Limita o tamanho da resposta

            // 3. Enviar Pedido REST
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

            // 4. Extrair a resposta do JSON complexo da OpenAI
            // Caminho: choices[0] -> message -> content
            if (response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }

        } catch (Exception e) {
            System.err.println("Erro ao chamar OpenAI: " + e.getMessage());
            // Em caso de erro (quota excedida, sem net), devolve null para não estragar a página
            return null;
        }
        return null;
    }
}