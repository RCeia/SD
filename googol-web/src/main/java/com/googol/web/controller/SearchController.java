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

/**
 * Controlador principal da aplicação Web (Spring MVC).
 * <p>
 * Esta classe gere as requisições HTTP para a interface do utilizador, incluindo:
 * <ul>
 * <li>Apresentação da página inicial e resultados de pesquisa.</li>
 * <li>Lógica de paginação e extração de metadados especiais.</li>
 * <li>Integração com serviços externos (OpenAI e Hacker News) para enriquecer a experiência.</li>
 * <li>Submissão de URLs para indexação.</li>
 * </ul>
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
@Controller
public class SearchController {

    private final GoogolService googolService;
    private final OpenAIService openAIService;
    private final HackerNewsService hackerNewsService;

    /**
     * Construtor do controlador com injeção de dependências.
     *
     * @param googolService Serviço de comunicação com o Gateway RMI.
     * @param openAIService Serviço para geração de resumos via IA.
     * @param hackerNewsService Serviço de integração com a API do Hacker News.
     */
    public SearchController(GoogolService googolService,
                            OpenAIService openAIService,
                            HackerNewsService hackerNewsService) {
        this.googolService = googolService;
        this.openAIService = openAIService;
        this.hackerNewsService = hackerNewsService;
    }

    /**
     * Trata os pedidos GET para a página inicial ("/") e para a exibição de resultados de pesquisa.
     * <p>
     * Este método realiza várias operações complexas:
     * 1. Solicita a pesquisa ao serviço Googol.
     * 2. Processa um metadado especial ({@code ##META_STATS##}) para obter o número total real de resultados,
     * permitindo o cálculo correto da paginação.
     * 3. Se for a primeira página, solicita um resumo gerado por IA (OpenAI).
     * 4. Preenche o {@code Model} com os dados necessários para a template Thymeleaf.
     * </p>
     *
     * @param query A string de pesquisa (opcional).
     * @param page O número da página atual (predefinição: 1).
     * @param model O modelo para passar dados para a vista.
     * @return O nome da vista a ser renderizada ("index").
     */
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

    /**
     * Trata o pedido POST para indexar notícias do Hacker News baseadas num termo.
     * <p>
     * Pesquisa na API do Hacker News e envia os URLs encontrados para a fila de indexação do Googol.
     * </p>
     *
     * @param query O termo a pesquisar no Hacker News.
     * @param attrs Atributos para passar mensagens "flash" (sucesso/erro) após o redirecionamento.
     * @return Redireciona para a página inicial.
     */
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

    /**
     * Trata o pedido POST para a indexação manual de um URL específico.
     *
     * @param url O URL a ser indexado.
     * @param attrs Atributos para passar mensagens de feedback ao utilizador.
     * @return Redireciona para a página inicial.
     */
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

    /**
     * Trata o pedido GET para visualizar os links que apontam para um determinado URL (Backlinks).
     *
     * @param url O URL alvo para o qual se pretendem ver os links de entrada.
     * @param model O modelo para passar a lista de links para a vista.
     * @return O nome da vista a ser renderizada ("links").
     */
    @GetMapping("/links")
    public String incomingLinks(@RequestParam("url") String url, Model model) {
        List<String> links = googolService.getIncomingLinks(url);
        model.addAttribute("targetUrl", url);
        model.addAttribute("incomingLinks", links);
        return "links";
    }
}