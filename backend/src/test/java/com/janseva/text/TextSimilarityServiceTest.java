package com.janseva.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextSimilarityServiceTest {

    private final TextSimilarityService service = new TextSimilarityService();

    @Test
    void similarDescriptionsProduceHigherSimilarityThanUnrelated() {
        String a = "Garbage is accumulating near the school entrance.";
        String b = "Large garbage pile near school main gate.";
        String unrelated = "Streetlight is not working in the park.";

        double similar = service.jaccard(a, b);
        double different = service.jaccard(a, unrelated);

        assertThat(similar).isGreaterThan(different);
        assertThat(similar).isGreaterThan(0.2);
    }

    @Test
    void identicalTextIsPerfectMatch() {
        assertThat(service.jaccard("Water leaking from pipe", "Water leaking from pipe")).isEqualTo(1.0);
    }

    @Test
    void emptyTextHandledSafely() {
        assertThat(service.jaccard("", "")).isZero();
        assertThat(service.jaccard("some text", "")).isZero();
        assertThat(service.jaccard(null, "some text")).isZero();
    }

    @Test
    void normalizationIgnoresCasePunctuationAndStopwords() {
        assertThat(service.jaccard("The GARBAGE! is dumped, here.",
                "garbage dumped here")).isEqualTo(1.0);
    }
}
