package adaptivestopwords; // Coloque no mesmo pacote do Downloader

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Classe utilitária responsável pela tokenização de texto.
 * <p>
 * Transforma texto bruto em listas de palavras processadas, lidando com
 * normalização (minúsculas) e limpeza (remoção de caracteres especiais).
 * </p>
 *
 * @author Ivan, Rodrigo e Samuel
 * @version 1.0
 */
public class Tokenizer {

    /**
     * Construtor padrão do Tokenizer.
     */
    public Tokenizer() {
    }

    /**
     * Divide uma string de texto numa lista de tokens (palavras).
     * <p>
     * O processo de tokenização segue os passos:
     * <ul>
     * <li>Verifica se o texto é nulo ou vazio.</li>
     * <li>Converte todo o texto para minúsculas.</li>
     * <li>Remove todos os caracteres que não sejam letras (Unicode) ou espaços.</li>
     * <li>Divide a string resultante por espaços em branco.</li>
     * <li>Filtra tokens vazios resultantes de múltiplos espaços.</li>
     * </ul>
     * </p>
     *
     * @param text O texto original a ser processado.
     * @return Uma lista contendo as palavras extraídas. Retorna uma lista vazia se a entrada for inválida.
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        // Converte para minúsculas.
        String lowerCaseText = text.toLowerCase();

        // Remove tudo exceto letras e espaços.
        String cleanText = lowerCaseText.replaceAll("[^\\p{L}\\s]+", " ");

        // Divide por espaços e remove tokens vazios.
        return Arrays.stream(cleanText.split("\\s+"))
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toList());
    }
}