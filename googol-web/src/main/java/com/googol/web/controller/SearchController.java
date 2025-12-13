package com.googol.web.controller;

import com.googol.web.service.GoogolService;
import com.googol.web.service.OpenAIService;
import com.googol.web.service.HackerNewsService;
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
    private final HackerNewsService hackerNewsService;

    public SearchController(GoogolService googolService,
                            OpenAIService openAIService,
                            HackerNewsService hackerNewsService) {
        this.googolService = googolService;
        this.openAIService = openAIService;
        this.hackerNewsService = hackerNewsService;
    }

    @GetMapping("/")
    public String index(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "1") int page,
            Model model
    ) {
        if (query != null && !query.trim().isEmpty()) {
            if (page < 1) page = 1;

            // 1. Obter resultados brutos
            Map<String, UrlMetadata> resultsMap = googolService.search(query, page);

            // 2. O TRUQUE: Procurar o total escondido
            int totalResults = 0;
            if (resultsMap.containsKey("##META_STATS##")) {
                try {
                    // Lemos o número que guardámos na descrição
                    String strCount = resultsMap.get("##META_STATS##").getCitation();
                    totalResults = Integer.parseInt(strCount);
                } catch (Exception e) {
                    totalResults = resultsMap.size(); // Fallback
                }
                // IMPORTANTE: Remover do mapa para não aparecer como resultado visual
                resultsMap.remove("##META_STATS##");
            } else {
                // Se não vier stat (ex: lista vazia), o total é o tamanho da lista
                totalResults = resultsMap.size();
            }

            List<Map.Entry<String, UrlMetadata>> pageResults = new ArrayList<>(resultsMap.entrySet());

            // 3. Calcular Paginação Real
            int pageSize = 10;
            // Math.ceil precisa de double para funcionar bem
            int totalPages = (int) Math.ceil((double) totalResults / pageSize);

            // C. IA (Apenas página 1)
            if (page == 1) {
                String aiSummary = openAIService.generateSummary(query);
                if (aiSummary != null) model.addAttribute("aiSummary", aiSummary);
            }

            model.addAttribute("results", pageResults);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("totalResults", totalResults); // Agora já não é null!
            model.addAttribute("query", query);
        }
        return "index";
    }

    // --- 2. INDEXAR HACKER NEWS (MANTÉM-SE IGUAL) ---
    @PostMapping("/hacker-news/index")
    public String indexHackerNews(@RequestParam("query") String query, RedirectAttributes attrs) {
        List<String> urls = hackerNewsService.searchAndGetUrls(query);

        if (urls.isEmpty()) {
            attrs.addFlashAttribute("message", "Hacker News: Nenhuma 'top story' encontrada com o termo '" + query + "'.");
            attrs.addFlashAttribute("msgType", "error");
        } else {
            int count = 0;
            for (String url : urls) {
                if (googolService.indexURL(url)) {
                    count++;
                }
            }
            attrs.addFlashAttribute("message", "Hacker News: " + count + " URLs enviados para indexação sobre '" + query + "'.");
            attrs.addFlashAttribute("msgType", "success");
        }
        attrs.addAttribute("q", query);
        return "redirect:/";
    }

    // --- 3. INDEXAR URL MANUAL (MANTÉM-SE IGUAL) ---
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

    // --- 4. LINKS DE ENTRADA (MANTÉM-SE IGUAL) ---
    @GetMapping("/links")
    public String incomingLinks(@RequestParam("url") String url, Model model) {
        List<String> links = googolService.getIncomingLinks(url);
        model.addAttribute("targetUrl", url);
        model.addAttribute("incomingLinks", links);
        return "links";
    }
}