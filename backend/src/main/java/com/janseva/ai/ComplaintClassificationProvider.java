package com.janseva.ai;

import java.util.List;

public interface ComplaintClassificationProvider {

    String modelName();

    String modelVersion();

    String method();

    ClassificationResult classify(String title, String description, List<String> categoryCodes);

    record ClassificationResult(
            String categoryCode,
            double confidence,
            String explanation,
            String method
    ) {
    }
}
