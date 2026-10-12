package com.university.docassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.docassistant.config.AiProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiClientServiceTest {

    private static final String URL = "http://ai.test/v1/chat/completions";
    private static final String REPLY = """
            {"choices": [{"message": {"role": "assistant", "content": "SYLLABUS"}}]}""";

    private final List<Duration> sleeps = new ArrayList<>();
    private MockRestServiceServer server;
    private AiClientService client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(true, "secret-key", "openai/gpt-oss-120b", "http://ai.test/v1/",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1_000, 1, 1, 1, 3);
        client = new AiClientService(properties, new ObjectMapper(), builder, sleeps::add);
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted(); // Clears a flag a test may have set, so it cannot leak into other tests.
    }

    @Test
    void rateLimitedThenSuccessfulReturnsTheContentAfterWaitingRetryAfter() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3"));
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-key"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(client.chat("system", "user")).contains("SYLLABUS");
        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void unauthorizedIsNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.chat("system", "user")).isEmpty();
        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void persistentServerErrorsGiveUpAfterMaxAttemptsWithBackoff() {
        server.expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(client.chat("system", "user")).isEmpty();
        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void retryAfterIsCappedAt20Seconds() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "3600"));
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(client.chat("system", "user")).contains("SYLLABUS");
        assertThat(sleeps).containsExactly(Duration.ofSeconds(20));
    }

    @Test
    void interruptedWaitGivesUpAndKeepsTheInterruptFlag() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer interruptedServer = MockRestServiceServer.bindTo(builder).build();
        AiClientService interrupted = new AiClientService(
                new AiProperties(true, "k", "m", "http://ai.test/v1", Duration.ofSeconds(1), Duration.ofSeconds(1),
                        1_000, 1, 1, 1, 3),
                new ObjectMapper(), builder, duration -> {
                    throw new InterruptedException();
                });
        interruptedServer.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withServerError());

        assertThat(interrupted.chat("system", "user")).isEmpty();
        interruptedServer.verify();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void chatJsonAsksForJsonModeAndChatDoesNot() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andExpect(jsonPath("$.temperature").value(0.1))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(client.chatJson("Answer in JSON", "user")).contains("SYLLABUS");
        assertThat(client.chat("system", "user")).contains("SYLLABUS");
        server.verify();
    }

    private static final String MODELS_URL = "http://ai.test/v1/models";
    private static final String MODELS = """
            {"object": "list", "data": [
              {"id": "openai/gpt-oss-120b"}, {"id": "openai/gpt-oss-20b"}, {"id": "whisper-large-v3"},
              {"id": "canopylabs/orpheus-v1-english"}, {"id": "meta-llama/llama-guard-4-12b"}]}""";

    @Test
    void startupCheckFindsTheConfiguredModel() {
        server.expect(ExpectedCount.once(), requestTo(MODELS_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-key"))
                .andRespond(withSuccess(MODELS, MediaType.APPLICATION_JSON));

        assertThat(client.checkConfiguredModel()).isEqualTo(AiClientService.ModelCheck.AVAILABLE);
        server.verify();
    }

    @Test
    void startupCheckReportsAModelTheKeyCannotUse() {
        // The regression this check exists for: AI_MODEL=llama-3.3-70b-versatile with a key that has no access to it.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer modelsServer = MockRestServiceServer.bindTo(builder).build();
        AiClientService wrongModel = new AiClientService(new AiProperties(true, "secret-key", "llama-3.3-70b-versatile",
                "http://ai.test/v1", Duration.ofSeconds(1), Duration.ofSeconds(1), 1_000, 1, 1, 1, 3),
                new ObjectMapper(), builder, sleeps::add);
        modelsServer.expect(ExpectedCount.once(), requestTo(MODELS_URL))
                .andRespond(withSuccess(MODELS, MediaType.APPLICATION_JSON));

        assertThat(wrongModel.checkConfiguredModel()).isEqualTo(AiClientService.ModelCheck.MISSING);
        modelsServer.verify();
    }

    @Test
    void startupCheckSurvivesAFailedRequestWithoutRetrying() {
        server.expect(ExpectedCount.once(), requestTo(MODELS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.checkConfiguredModel()).isEqualTo(AiClientService.ModelCheck.FAILED);
        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void startupCheckIsSkippedWithoutARealKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer noCalls = MockRestServiceServer.bindTo(builder).build();
        AiClientService unconfigured = new AiClientService(new AiProperties(true, AiProperties.DEMO_KEY, "m",
                "http://ai.test/v1", Duration.ofSeconds(1), Duration.ofSeconds(1), 1_000, 1, 1, 1, 3),
                new ObjectMapper(), builder, sleeps::add);

        assertThat(unconfigured.checkConfiguredModel()).isEqualTo(AiClientService.ModelCheck.SKIPPED);
        noCalls.verify(); // No request was expected, and none was made.
    }

    @Test
    void chatModelFilterDropsSpeechAndModerationModels() {
        assertThat(AiClientService.looksLikeChatModel("openai/gpt-oss-120b")).isTrue();
        assertThat(AiClientService.looksLikeChatModel("qwen/qwen3.8-27b")).isTrue();
        assertThat(AiClientService.looksLikeChatModel("whisper-large-v3")).isFalse();
        assertThat(AiClientService.looksLikeChatModel("canopylabs/orpheus-v1-english")).isFalse();
        assertThat(AiClientService.looksLikeChatModel("meta-llama/llama-guard-4-12b")).isFalse();
    }

    @Test
    void theStartupLogDescribesTheKeyWithoutRevealingIt() {
        String key = "gsk_" + "x".repeat(52);
        assertThat(AiClientService.describeKey(key)).isEqualTo("present (56 chars)").doesNotContain("gsk_");
        assertThat(AiClientService.describeKey(AiProperties.DEMO_KEY)).isEqualTo("is the placeholder demo-key");
        assertThat(AiClientService.describeKey(null)).isEqualTo("missing");
        assertThat(AiClientService.describeKey("  ")).isEqualTo("missing");
    }

    @Test
    void missingOrInvalidMaxAttemptsDefaultsToThree() {
        assertThat(new AiProperties(true, "k", "m", "u", null, null, 0, 0, 0, 0, 0).maxAttempts()).isEqualTo(3);
        assertThat(new AiProperties(true, "k", "m", "u", null, null, 0, 0, 0, 0, -2).maxAttempts()).isEqualTo(3);
        assertThat(new AiProperties(true, "k", "m", "u", null, null, 0, 0, 0, 0, 5).maxAttempts()).isEqualTo(5);
    }
}
