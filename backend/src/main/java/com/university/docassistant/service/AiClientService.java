package com.university.docassistant.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.docassistant.config.AiProperties;
import com.university.docassistant.config.SystemTrust;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;

/**
 * Minimal client of an OpenAI-compatible Chat Completions API ({@code POST {base-url}/chat/completions}).
 * Never throws: a disabled or unconfigured client, a timeout, an HTTP error or an odd response all yield an
 * empty result and a log line, so callers can always fall back to "no AI".
 */
@Slf4j
@Service
public class AiClientService {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AiClientService(AiProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        // HTTP/1.1 without an h2c upgrade attempt: plain-http OpenAI-compatible servers (local models, proxies)
        // do not all support it. HTTPS endpoints work the same either way.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .sslContext(SystemTrust.sslContext())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = builder
                .baseUrl(properties.baseUrl() == null ? "" : properties.baseUrl().replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
        log.info("AI client: {}", properties.isConfigured()
                ? "enabled, model " + properties.model() + " at " + properties.baseUrl()
                : "disabled (AI_ENABLED=false or no real AI_API_KEY); documents are stored without AI analysis");
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
        if (!properties.isConfigured()) {
            log.debug("AI call skipped: client is not configured");
            return Optional.empty();
        }
        ChatRequest request = new ChatRequest(properties.model(), List.of(
                new Message("system", systemPrompt),
                new Message("user", userMessage)), 0.1);
        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    // Serialized up front so the request carries Content-Length; a streamed body would be sent
                    // chunked, which some OpenAI-compatible servers reject.
                    .body(objectMapper.writeValueAsBytes(request))
                    .retrieve()
                    .body(JsonNode.class);
            String content = response == null ? null : response.at("/choices/0/message/content").asText(null);
            if (content == null || content.isBlank()) {
                log.warn("AI API returned no message content");
                return Optional.empty();
            }
            return Optional.of(content);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize AI request: {}", ex.getMessage());
        } catch (RestClientResponseException ex) {
            // The body may echo the request; only status and a short prefix are logged.
            String body = ex.getResponseBodyAsString();
            log.warn("AI API responded {} {}", ex.getStatusCode().value(),
                    body.length() > 300 ? body.substring(0, 300) + "..." : body);
        } catch (RestClientException ex) {
            // Timeouts, refused connections, unreadable responses.
            // The JDK client's exceptions often have no message; the root cause names what happened.
            log.warn("AI API call to {} failed: {}", properties.baseUrl(), ex.getMostSpecificCause().toString());
        } catch (RuntimeException ex) {
            log.warn("AI API call failed unexpectedly", ex);
        }
        return Optional.empty();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatRequest(String model, List<Message> messages, Double temperature) {
    }

    private record Message(String role, String content) {
    }
}
