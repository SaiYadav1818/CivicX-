package com.janseva.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedClassificationProviderTest {

    private final RuleBasedClassificationProvider provider = new RuleBasedClassificationProvider();

    private static final List<String> ALL = List.of(
            "GARBAGE", "ROAD_POTHOLE", "DAMAGED_ROAD", "WATER_LEAKAGE", "DRAINAGE_BLOCKAGE",
            "MANHOLE_OVERFLOW", "STREETLIGHT", "OPEN_DRAIN", "BROKEN_FOOTPATH", "FALLEN_TREE",
            "PUBLIC_PROPERTY_DAMAGE", "WATERLOGGING", "OTHER");

    @Test
    void identifiesGarbageComplaint() {
        var result = provider.classify("Garbage dump near school",
                "Large garbage pile with plastic waste has accumulated near the school entrance.", ALL);

        assertThat(result.categoryCode()).isEqualTo("GARBAGE");
        assertThat(result.confidence()).isGreaterThan(0.4);
        assertThat(result.method()).isEqualTo("RULE_BASED");
    }

    @Test
    void identifiesPotholeComplaint() {
        var result = provider.classify("Road damaged",
                "There is a big pothole in the middle of the road and it is dangerous for vehicles.", ALL);

        assertThat(result.categoryCode()).isEqualTo("ROAD_POTHOLE");
    }

    @Test
    void identifiesWaterLeakageComplaint() {
        var result = provider.classify("Water problem",
                "Water is continuously leaking from a broken pipeline near the junction.", ALL);

        assertThat(result.categoryCode()).isEqualTo("WATER_LEAKAGE");
    }

    @Test
    void unknownTextFallsBackToOtherWithLowConfidence() {
        var result = provider.classify("Something strange",
                "I saw something unusual happening in the area yesterday evening.", ALL);

        assertThat(result.categoryCode()).isEqualTo("OTHER");
        assertThat(result.confidence()).isLessThan(0.4);
    }

    @Test
    void emptyTextFallsBackToOther() {
        var result = provider.classify("", "", ALL);

        assertThat(result.categoryCode()).isEqualTo("OTHER");
        assertThat(result.confidence()).isLessThan(0.4);
    }

    @Test
    void methodIsExplicitlyRuleBasedNotAi() {
        var result = provider.classify("garbage", "garbage", ALL);

        assertThat(result.method()).isEqualTo("RULE_BASED");
        assertThat(provider.modelName()).contains("rule-based");
        assertThat(result.explanation()).contains("RULE_BASED");
    }

    @Test
    void respectsProvidedCategoryWhitelist() {
        var result = provider.classify("Garbage", "Garbage everywhere", List.of("WATERLOGGING"));

        assertThat(result.categoryCode()).isEqualTo("OTHER");
    }
}
