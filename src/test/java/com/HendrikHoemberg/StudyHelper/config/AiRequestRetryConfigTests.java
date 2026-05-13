package com.HendrikHoemberg.StudyHelper.config;

import com.google.genai.Client;
import com.google.genai.types.HttpRetryOptions;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.core.retry.RetryTemplate;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequestRetryConfigTests {

    @Test
    void springAiRetryMaxAttemptsZero_AllowsOnlyInitialAttempt() {
        SpringAiRetryProperties properties = new SpringAiRetryProperties();
        properties.setMaxAttempts(0);
        RetryTemplate retryTemplate = new SpringAiRetryAutoConfiguration().retryTemplate(properties);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryTemplate.execute(() -> {
            attempts.incrementAndGet();
            throw new TransientAiException("temporary");
        })).isInstanceOf(Exception.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    void geminiClient_DisablesGoogleSdkHttpRetries() throws Exception {
        Client client = new GeminiClientConfig().googleGenAiClient("test-key");

        Object retryInterceptor = retryInterceptor(client);
        HttpRetryOptions retryOptions = retryOptions(retryInterceptor);

        assertThat(retryOptions.attempts()).contains(1);
    }

    private Object retryInterceptor(Client client) throws Exception {
        Object apiClient = field(client, "apiClient");
        OkHttpClient httpClient = field(apiClient, "httpClient");
        for (Interceptor interceptor : httpClient.interceptors()) {
            if (interceptor.getClass().getName().equals("com.google.genai.RetryInterceptor")) {
                return interceptor;
            }
        }
        throw new AssertionError("Google GenAI RetryInterceptor was not registered");
    }

    @SuppressWarnings("unchecked")
    private <T> T field(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private HttpRetryOptions retryOptions(Object retryInterceptor) throws Exception {
        Field field = retryInterceptor.getClass().getDeclaredField("retryOptions");
        field.setAccessible(true);
        return (HttpRetryOptions) field.get(retryInterceptor);
    }
}
