package com.janseva.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedClassificationProvider implements ComplaintClassificationProvider {

    public static final String MODEL_NAME = "civicx-rule-based-classifier";
    public static final String MODEL_VERSION = "1.0";
    public static final String METHOD = "RULE_BASED";

    private static final String OTHER = "OTHER";

    private static final Map<String, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry("GARBAGE", List.of("garbage", "waste", "trash", "rubbish", "dump", "dumping", "litter", "debris", "uncollected bin")),
            Map.entry("ROAD_POTHOLE", List.of("pothole", "potholes", "crater on the road", "hole in the road", "road caved")),
            Map.entry("DAMAGED_ROAD", List.of("damaged road", "broken road", "cracked road", "road damage", "road surface broken", "road breaking")),
            Map.entry("WATER_LEAKAGE", List.of("water leak", "water leakage", "leaking water", "pipe burst", "burst pipe", "leaking pipe", "pipeline leak", "broken pipeline", "water pipe", "tap leaking")),
            Map.entry("DRAINAGE_BLOCKAGE", List.of("drainage", "drain blocked", "blocked drain", "clogged drain", "drainage blocked", "sewer blocked", "choked drain")),
            Map.entry("MANHOLE_OVERFLOW", List.of("manhole", "sewage overflow", "manhole overflow", "overflowing manhole", "sewage flowing")),
            Map.entry("STREETLIGHT", List.of("streetlight", "street light", "streetlight not working", "lamp post", "light not working", "no street light", "dark road")),
            Map.entry("OPEN_DRAIN", List.of("open drain", "uncovered drain", "drain without cover", "broken drain cover")),
            Map.entry("BROKEN_FOOTPATH", List.of("footpath", "footpath broken", "sidewalk", "pavement broken", "broken pavement", "footpath damaged")),
            Map.entry("FALLEN_TREE", List.of("fallen tree", "tree fell", "tree fallen", "uprooted tree", "tree blocking", "fallen branch")),
            Map.entry("PUBLIC_PROPERTY_DAMAGE", List.of("public property", "property damage", "vandalism", "vandalised", "vandalized", "damaged bus stop", "broken bench")),
            Map.entry("WATERLOGGING", List.of("waterlogging", "water logging", "stagnant water", "flooded", "flooding", "waterlogged"))
    );

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    @Override
    public String modelVersion() {
        return MODEL_VERSION;
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public ClassificationResult classify(String title, String description, List<String> categoryCodes) {
        String text = normalize(title) + " " + normalize(description);

        String bestCategory = OTHER;
        int bestMatches = 0;
        for (Map.Entry<String, List<String>> entry : KEYWORDS.entrySet()) {
            if (!categoryCodes.isEmpty() && !categoryCodes.contains(entry.getKey())) {
                continue;
            }
            int matches = countMatches(text, entry.getValue());
            if (matches > bestMatches) {
                bestMatches = matches;
                bestCategory = entry.getKey();
            }
        }

        if (bestMatches == 0) {
            return new ClassificationResult(
                    OTHER,
                    0.15,
                    "No specific civic issue keywords matched the description. "
                            + "This suggestion is RULE_BASED keyword matching, not an AI model.",
                    METHOD
            );
        }

        double confidence = Math.min(0.90, 0.35 + 0.12 * bestMatches);
        return new ClassificationResult(
                bestCategory,
                confidence,
                "Matched " + bestMatches + " keyword(s) associated with " + bestCategory + ". "
                        + "This suggestion is RULE_BASED keyword matching, not an AI model.",
                METHOD
        );
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase();
    }

    private int countMatches(String text, List<String> keywords) {
        int count = 0;
        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                count++;
            }
        }
        return count;
    }
}
