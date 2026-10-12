package com.university.docassistant.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.docassistant.config.AiProperties;
import com.university.docassistant.config.SystemTrust;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Minimal client of an OpenAI-compatible Chat Completions API ({@code POST {base-url}/chat/completions}).
 * Never throws: a disabled or unconfigured client, a timeout, an HTTP error or an odd response all yield an
 * empty result and a log line, so callers can always fall back to "no AI".
 * <p>
 * Rate limits (429) and server errors (5xx) are retried up to {@code app.ai.max-attempts} times in total, waiting
 * for the Retry-After header (capped at {@value #MAX_RETRY_AFTER_SECONDS} s) or else 2 s, 4 s, ... Other 4xx answers
 * are final, and so are timeouts and connection errors, which have already waited for the configured timeout.
 */
@Slf4j
@Service
public class AiClientService {

    static final long MAX_RETRY_AFTER_SECONDS = 20;
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(2);
    private static final ResponseFormat JSON_OBJECT = new ResponseFormat("json_object");

    /** Waits between retries; replaced in tests so they do not actually sleep. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Sleeper sleeper;
    /** The configured base URL without trailing slashes, as requests use it. */
    private final String baseUrl;

    @Autowired
    public AiClientService(AiProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this(properties, objectMapper, builder.requestFactory(requestFactory(properties)),
                duration -> Thread.sleep(duration.toMillis()));
    }

    /** {@code builder} comes with its request factory set (the JDK client above, or a mock server in tests). */
    AiClientService(AiProperties properties, ObjectMapper objectMapper, RestClient.Builder builder, Sleeper sleeper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
        this.baseUrl = properties.baseUrl() == null ? "" : properties.baseUrl().replaceAll("/+$", "");
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
        log.info("AI client: enabled={}, base-url {}, model {}, max-attempts {}, max-concurrency {}, API key {}{}",
                properties.enabled(), baseUrl, properties.model(), properties.maxAttempts(),
                properties.maxConcurrency(), describeKey(properties.apiKey()),
                properties.isConfigured() ? ""
                        : " - AI is off (AI_ENABLED=false or no real AI_API_KEY); documents are stored without AI analysis");
    }

    /** Presence and length only: the key itself never reaches the log. */
    static String describeKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "missing";
        }
        if (AiProperties.DEMO_KEY.equals(apiKey)) {
            return "is the placeholder " + AiProperties.DEMO_KEY;
        }
        return "present (" + apiKey.length() + " chars)";
    }

    /** Outcome of {@link #checkConfiguredModel()}. */
    enum ModelCheck {
        SKIPPED, AVAILABLE, MISSING, FAILED
    }

    /**
     * Once the application is up (so a slow provider never delays startup): makes sure the configured model exists
     * for this key. A wrong AI_MODEL otherwise only shows up as a 404 on the first user request.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkModelAtStartup() {
        checkConfiguredModel();
    }

    /**
     * One GET {base-url}/models, with the configured timeouts and without retries. Logs INFO when the model is
     * listed, WARN when it is not (with the chat models that are) or when the request fails. Never throws.
     */
    ModelCheck checkConfiguredModel() {
        if (!properties.isConfigured()) {
            return ModelCheck.SKIPPED;
        }
        String model = properties.model();
        try {
            JsonNode response = restClient.get().uri("/models").retrieve().body(JsonNode.class);
            JsonNode data = response == null ? null : response.get("data");
            if (data == null || !data.isArray()) {
                log.warn("Could not check AI model {}: GET {}/models returned no model list", model, baseUrl);
                return ModelCheck.FAILED;
            }
            List<String> ids = new ArrayList<>();
            data.forEach(item -> {
                String id = item.path("id").asText("");
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            });
            if (ids.contains(model)) {
                log.info("AI model {} is available for this API key", model);
                return ModelCheck.AVAILABLE;
            }
            List<String> chatModels = ids.stream().filter(AiClientService::looksLikeChatModel).sorted().toList();
            log.warn("Configured AI model {} is not available for this API key; available chat models: {}. "
                    + "Every AI call will fail until AI_MODEL is changed.", model,
                    chatModels.isEmpty() ? "(none)" : String.join(", ", chatModels));
            return ModelCheck.MISSING;
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            log.warn("Could not check AI model {}: GET {}/models responded {} {}", model, baseUrl,
                    ex.getStatusCode().value(), body.length() > 300 ? body.substring(0, 300) + "..." : body);
        } catch (RestClientException ex) {
            log.warn("Could not check AI model {}: GET {}/models failed: {}", model, baseUrl,
                    ex.getMostSpecificCause().toString());
        } catch (RuntimeException ex) {
            log.warn("Could not check AI model {}: {}", model, ex.toString());
        }
        return ModelCheck.FAILED;
    }

    /** /models also lists speech, TTS, moderation and embedding models; they cannot serve chat completions. */
    static boolean looksLikeChatModel(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        return !(lower.contains("whisper") || lower.contains("tts") || lower.contains("orpheus")
                || lower.contains("guard") || lower.contains("embed"));
    }

    private static JdkClientHttpRequestFactory requestFactory(AiProperties properties) {
        // HTTP/1.1 without an h2c upgrade attempt: plain-http OpenAI-compatible servers (local models, proxies)
        // do not all support it. HTTPS endpoints work the same either way.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .sslContext(SystemTrust.sslContext())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /** AI_ENABLED as configured, whether or not a usable key is present. */
    public boolean isEnabled() {
        return properties.enabled();
    }

    /**
     * Sends one system + user message pair and returns the assistant's reply text,
     * or empty if the client is not configured or the call failed for any reason.
     */
    public Optional<String> chat(String systemPrompt, String userMessage) {
        return complete(systemPrompt, userMessage, null);
    }

    /**
     * Like {@link #chat}, but asks for JSON mode ({@code "response_format": {"type": "json_object"}}), so the reply
     * is a single JSON object. The prompt must still mention JSON: OpenAI-compatible APIs reject JSON mode otherwise.
     */
    public Optional<String> chatJson(String systemPrompt, String userMessage) {
        return complete(systemPrompt, userMessage, JSON_OBJECT);
    }

    private Optional<String> complete(String systemPrompt, String userMessage, ResponseFormat responseFormat) {
        if (!properties.isConfigured()) {
            log.debug("AI call skipped: client is not configured");
            return Optional.empty();
        }
        ChatRequest request = new ChatRequest(properties.model(), List.of(
                new Message("system", systemPrompt),
                new Message("user", userMessage)), 0.1, responseFormat);
        byte[] body;
        try {
            // Serialized up front so the request carries Content-Length; a streamed body would be sent
            // chunked, which some OpenAI-compatible servers reject.
            body = objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize AI request: {}", ex.getMessage());
            return Optional.empty();
        }

        int maxAttempts = properties.maxAttempts();
        for (int attempt = 1; ; attempt++) {
            try {
                return send(body);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (attempt < maxAttempts && isRetryable(status)) {
                    // Status and attempt only: the key and the request body never reach the log.
                    log.warn("AI API responded {}, retrying (attempt {} of {})", status, attempt + 1, maxAttempts);
                    if (!pause(retryDelay(ex.getResponseHeaders(), attempt))) {
                        return Optional.empty();
                    }
                    continue;
                }
                // The body may echo the request; only status and a short prefix are logged.
                String responseBody = ex.getResponseBodyAsString();
                log.warn("AI API responded {} {}", status,
                        responseBody.length() > 300 ? responseBody.substring(0, 300) + "..." : responseBody);
            } catch (RestClientException ex) {
                // Timeouts, refused connections, unreadable responses: not retried, they already took the timeout.
                // The JDK client's exceptions often have no message; the root cause names what happened.
                log.warn("AI API call to {} failed: {}", properties.baseUrl(), ex.getMostSpecificCause().toString());
            } catch (RuntimeException ex) {
                log.warn("AI API call failed unexpectedly", ex);
            }
            return Optional.empty();
        }
    }

    private Optional<String> send(byte[] body) {
        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        String content = response == null ? null : response.at("/choices/0/message/content").asText(null);
        if (content == null || content.isBlank()) {
            log.warn("AI API returned no message content");
            return Optional.empty();
        }
        return Optional.of(content);
    }

    private static boolean isRetryable(int status) {
        return status == 429 || (status >= 500 && status <= 599);
    }

    /**
     * Retry-After in seconds when the server sends one (capped), else 2 s, 4 s, 8 s ... for retry 1, 2, 3 ...
     * An HTTP-date Retry-After is not parsed and falls back to the backoff.
     */
    static Duration retryDelay(HttpHeaders headers, int attempt) {
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.strip());
                if (seconds >= 0) {
                    return Duration.ofSeconds(Math.min(seconds, MAX_RETRY_AFTER_SECONDS));
                }
            } catch (NumberFormatException ignored) {
                // Fall back to the backoff below.
            }
        }
        Duration backoff = FIRST_BACKOFF.multipliedBy(1L << Math.min(attempt - 1, 4));
        return backoff.compareTo(Duration.ofSeconds(MAX_RETRY_AFTER_SECONDS)) > 0
                ? Duration.ofSeconds(MAX_RETRY_AFTER_SECONDS) : backoff;
    }

    /** False when interrupted: the flag is restored and the call gives up. */
    private boolean pause(Duration delay) {
        try {
            sleeper.sleep(delay);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("AI API retry interrupted");
            return false;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatRequest(String model, List<Message> messages, Double temperature,
                               @JsonProperty("response_format") ResponseFormat responseFormat) {
    }

    private record Message(String role, String content) {
    }

    private record ResponseFormat(String type) {
    }
}
