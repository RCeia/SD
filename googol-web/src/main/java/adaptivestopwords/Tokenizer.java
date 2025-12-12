package adaptivestopwords; // Coloque no mesmo pacote do Downloader

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Tokenizer {

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