package com.joveo.supply.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String apiKey,
        int maxBriefChars
) {
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
