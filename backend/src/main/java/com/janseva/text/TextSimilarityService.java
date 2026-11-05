package com.janseva.text;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TextSimilarityService {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s]");

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "has", "have", "had",
            "there", "this", "that", "these", "those", "it", "of", "to", "in", "on", "at",
            "for", "with", "by", "from", "and", "or", "near", "beside", "very", "some", "many",
            "about", "over", "under", "into", "onto", "because", "since", "who", "which"
    );

    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        return NON_ALPHANUMERIC.matcher(text.toLowerCase()).replaceAll(" ")
                .replaceAll("\\s+", " ").trim();
    }

    public Set<String> tokens(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(normalized.split(" "))
                .filter(t -> !STOPWORDS.contains(t))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Jaccard similarity of the token sets: |A ∩ B| / |A ∪ B|.
     * Returns 0.0 when either side is empty.
     */
    public double jaccard(String textA, String textB) {
        Set<String> a = tokens(textA);
        Set<String> b = tokens(textB);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        long intersection = a.stream().filter(b::contains).count();
        Set<String> union = new java.util.HashSet<>(a);
        union.addAll(b);
        return (double) intersection / union.size();
    }
}
