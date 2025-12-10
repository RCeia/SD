package com.googol.web.controller;

import com.googol.web.service.GoogolService;
import common.UrlMetadata; // Importa a classe partilhada
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class SearchController {

    private final GoogolService googolService;

    // Injeção de dependência do Service
    public SearchController(GoogolService googolService) {
        this.googolService = googolService;
    }

    @GetMapping("/search") // Define o URL: http://localhost:8080/search
    public String searchPage(@RequestParam(name = "q", required = false) String query, Model model) {

        // Se o utilizador escreveu alguma coisa na pesquisa
        if (query != null && !query.trim().isEmpty()) {
            System.out.println("Controller: A pesquisar por -> " + query);

            // Chama o serviço (que fala com a Gateway RMI)
            Map<String, UrlMetadata> results = googolService.search(query);

            // Envia os resultados para o HTML
            model.addAttribute("results", results);
            model.addAttribute("query", query);
        }

        // Retorna o nome do ficheiro HTML (sem .html)
        return "search";
    }
}