package downloader; // Coloque no mesmo pacote do Downloader

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Tokenizer {

    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }


        String lowerCaseText = text.toLowerCase();

        String cleanText = lowerCaseText.replaceAll("[^a-zà-ú0-9\\\\s]", "");

        return Arrays.stream(cleanText.split("\\s+"))
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toList());
    }
}