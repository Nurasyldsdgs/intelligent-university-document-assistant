package com.university.docassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of the OpenAI-compatible Chat Completions API used for task extraction ({@code app.ai.*}).
 *
 * @param maxInputChars  longer extracted texts are cut before being sent, which bounds the cost of one call
 * @param maxConcurrency analyses running at the same time; the rest wait in a queue of {@code queueCapacity}
 * @param maxConcurrentQuestions Q&A calls in flight at once; each holds a request thread while the model answers,
 *                               so this caps how many threads slow AI answers can occupy
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String apiKey,
        String model,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxInputChars,
        int maxConcurrency,
        int queueCapacity,
        int maxConcurrentQuestions
) {
    /** Placeholder key from application.yml: treated like a missing key. */
    public static final String DEMO_KEY = "demo-key";

    public AiProperties {
        // A key pasted with a stray space or newline would otherwise break the Authorization header.
        apiKey = apiKey == null ? null : apiKey.strip();
    }

    /** True only when a call can actually be made; otherwise analysis is skipped without contacting the API. */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank() && !DEMO_KEY.equals(apiKey)
                && baseUrl != null && !baseUrl.isBlank();
    }
}
