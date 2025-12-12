package com.googol.web.controller;

import com.googol.web.service.GoogolService;
import com.googol.web.service.OpenAIService;
import com.googol.web.service.HackerNewsService; // <--- NOVO IMPORT
import common.UrlMetadata;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class SearchController {

    private final GoogolService googolService;
    private final OpenAIService openAIService;
    private final HackerNewsService hackerNewsService; // <--- NOVO SERVIÇO

    // Injeção de dependência dos 3 serviços
    public SearchController(GoogolService googolService,
                            OpenAIService openAIService,
                            HackerNewsService hackerNewsService) {
        this.googolService = googolService;
        this.openAIService = openAIService;
        this.hackerNewsService = hackerNewsService;
    }

    // --- 1. PÁGINA PRINCIPAL E PESQUISA ---
    @GetMapping("/") // Indica que esta função responde quando alguém acede à raiz do site "localhost:8443"
    public String index(
            // Captura termos de pesquisa e número da página
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "1") int page,
            Model model // Pacote onde enviamos os dados para enviar para o HTML
    ) {
        if (query != null && !query.trim().isEmpty()) {
            // A. Resultados RMI
            Map<String, UrlMetadata> resultsMap = googolService.search(query);
            // ArrayList onde guardamos entradas map do tipo (URL, metadata)
            List<Map.Entry<String, UrlMetadata>> resultList = new ArrayList<>(resultsMap.entrySet());

            // B. Paginação
            int pageSize = 10;
            int totalResults = resultList.size();
            int totalPages = (int) Math.ceil((double) totalResults / pageSize);
            if (page < 1) page = 1;
            if (page > totalPages && totalPages > 0) page = totalPages;
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, totalResults);
            List<Map.Entry<String, UrlMetadata>> pageResults = new ArrayList<>();
            if (start < totalResults) pageResults = resultList.subList(start, end);

            // C. IA (Apenas página 1)
            if (page == 1) {
                String aiSummary = openAIService.generateSummary(query);
                if (aiSummary != null) model.addAttribute("aiSummary", aiSummary);
            }

            model.addAttribute("results", pageResults);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("totalResults", totalResults);
            model.addAttribute("query", query);
        }
        return "index"; // Diz ao Spring para renderizar o index.html
    }

    // --- 2. INDEXAR HACKER NEWS (ATUALIZADO) ---
    @PostMapping("/hacker-news/index")
    public String indexHackerNews(@RequestParam("query") String query, RedirectAttributes attrs) {

        // 1. Buscar URLs no Hacker News
        List<String> urls = hackerNewsService.searchAndGetUrls(query);

        if (urls.isEmpty()) {
            attrs.addFlashAttribute("message", "Hacker News: Nenhuma 'top story' encontrada com o termo '" + query + "'.");
            attrs.addFlashAttribute("msgType", "error");
        } else {
            // 2. Mandar indexar cada URL encontrado
            int count = 0;
            for (String url : urls) {
                if (googolService.indexURL(url)) {
                    count++;
                }
            }
            attrs.addFlashAttribute("message", "Hacker News: " + count + " URLs enviados para indexação sobre '" + query + "'.");
            attrs.addFlashAttribute("msgType", "success");
        }

        // Adiciona a query ao URL de destino.
        // O resultado será um redirecionamento para "/?q=termo_pesquisado"
        attrs.addAttribute("q", query);

        return "redirect:/"; // Reinicia a página evitando que o user reenvia se fizer F5
    }

    // --- 3. INDEXAR URL MANUAL ---
    @PostMapping("/index")
    public String indexUrl(@RequestParam("url") String url, RedirectAttributes attrs) {
        boolean success = googolService.indexURL(url);
        if (success) {
            attrs.addFlashAttribute("message", "URL enviado: " + url);
            attrs.addFlashAttribute("msgType", "success");
        } else {
            attrs.addFlashAttribute("message", "Erro ao conectar à Gateway.");
            attrs.addFlashAttribute("msgType", "error");
        }
        return "redirect:/";
    }

    // --- 4. LINKS DE ENTRADA ---
    @GetMapping("/links")
    public String incomingLinks(@RequestParam("url") String url, Model model) {
        List<String> links = googolService.getIncomingLinks(url);
        model.addAttribute("targetUrl", url);
        model.addAttribute("incomingLinks", links);
        return "links";
    }
}