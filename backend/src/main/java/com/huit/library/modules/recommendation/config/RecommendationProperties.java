package com.huit.library.modules.recommendation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "library.recommendation")
public class RecommendationProperties {
    private double contentWeight = 0.50;
    private double departmentWeight = 0.35;
    private double popularityWeight = 0.15;
    private int candidatePool = 3_000;
    private int historyLimit = 100;
    private int popularityWindowDays = 365;
    private long cacheTtlMinutes = 30;
}
