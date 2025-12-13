package common;

import java.io.Serializable;

/**
 * Classe de dados (DTO) que representa os metadados de um resultado de pesquisa.
 * <p>
 * Contém informações básicas sobre um URL encontrado, como o título e um excerto (citação)
 * do conteúdo, para serem exibidos nos resultados de pesquisa.
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class UrlMetadata implements Serializable {

    /**
     * O título da página web.
     */
    private final String title;

    /**
     * O excerto, citação ou snippet associado ao URL.
     */
    private final String citation;

    /**
     * Construtor da classe UrlMetadata.
     *
     * @param title O título da página.
     * @param citation O texto de citação ou resumo.
     */
    public UrlMetadata(String title, String citation) {
        this.title = title;
        this.citation = citation;
    }

    /**
     * Obtém o título da página.
     *
     * @return String contendo o título.
     */
    public String getTitle() { return title; }

    /**
     * Obtém a citação ou excerto da página.
     *
     * @return String contendo a citação.
     */
    public String getCitation() { return citation; }
}