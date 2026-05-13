package com.HendrikHoemberg.StudyHelper.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GeminiClientConfig {

    @Bean
    public Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey) {
        return Client.builder()
            .apiKey(apiKey)
            .httpOptions(HttpOptions.builder()
                .retryOptions(HttpRetryOptions.builder()
                    .attempts(1)
                    .build())
                .build())
            .build();
    }
}
