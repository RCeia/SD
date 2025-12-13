package common;

import java.io.Serializable;
import java.util.List;

/**
 * Classe que representa os dados processados de uma página Web.
 * <p>
 * Este objeto é criado pelo Downloader após o processamento de um URL e é
 * enviado para os Barrels para armazenamento e indexação.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class PageData implements Serializable {

    /**
     * Identificador para compatibilidade de serialização.
     */
    private static final long serialVersionUID = 1L;

    /**
     * O URL da página processada.
     */
    private String url;

    /**
     * O título extraído da página HTML.
     */
    private String title;

    /**
     * Lista de palavras tokenizadas encontradas na página.
     */
    private List<String> words;

    /**
     * Lista de URLs (links) encontrados na página que apontam para fora.
     */
    private List<String> outgoingLinks;

    /**
     * Construtor da classe PageData.
     *
     * @param url O URL da página.
     * @param title O título da página.
     * @param words A lista de palavras significativas encontradas.
     * @param outgoingLinks A lista de links extraídos.
     */
    public PageData(String url, String title, List<String> words, List<String> outgoingLinks) {
        this.url = url;
        this.title = title;
        this.words = words;
        this.outgoingLinks = outgoingLinks;
    }

    /**
     * Obtém o URL da página.
     *
     * @return String do URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Obtém o título da página.
     *
     * @return String do título.
     */
    public String getTitle() {
        return title;
    }

    /**
     * Obtém a lista de palavras da página.
     *
     * @return Lista de strings contendo as palavras.
     */
    public List<String> getWords() {
        return words;
    }

    /**
     * Obtém a lista de links de saída.
     *
     * @return Lista de strings contendo os URLs encontrados.
     */
    public List<String> getOutgoingLinks() {
        return outgoingLinks;
    }
}