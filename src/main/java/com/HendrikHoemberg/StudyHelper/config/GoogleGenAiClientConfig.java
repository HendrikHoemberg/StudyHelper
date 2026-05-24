package com.HendrikHoemberg.StudyHelper.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class GoogleGenAiClientConfig {

    static final int PROVIDER_TIMEOUT_MILLIS = 180_000;

    @Bean
    @ConditionalOnMissingBean(Client.class)
    Client googleGenAiClient(GoogleGenAiConnectionProperties properties) {
        Client.Builder builder = Client.builder()
            .httpOptions(HttpOptions.builder().timeout(PROVIDER_TIMEOUT_MILLIS).build());
        if (StringUtils.hasText(properties.getApiKey())) {
            builder.apiKey(properties.getApiKey());
        }
        if (properties.isVertexAi()) {
            builder.vertexAI(true)
                .project(properties.getProjectId())
                .location(properties.getLocation());
        }
        return builder.build();
    }
}
