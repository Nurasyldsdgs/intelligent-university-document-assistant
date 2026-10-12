package com.university.docassistant.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.docassistant.security.RestSecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every error response carries a stable code, so clients never have to compare message texts. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/courses/join");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void apiExceptionCodeAndRetryAfterReachTheResponse() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleApi(new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                "Слишком много попыток", ErrorCodes.TOO_MANY_ATTEMPTS, 120L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("120");
        JsonNode body = json.readTree(json.writeValueAsString(response.getBody()));
        assertThat(body.get("code").asText()).isEqualTo("TOO_MANY_ATTEMPTS");
        assertThat(body.get("message").asText()).isEqualTo("Слишком много попыток");
        assertThat(body.get("retryAfterSeconds").asLong()).isEqualTo(120);
        assertThat(body.get("status").asInt()).isEqualTo(429);
    }

    @Test
    void factoryMethodsCarryTheirCode() {
        ResponseEntity<ErrorResponse> response = handler.handleApi(
                ApiException.of(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.AI_UNAVAILABLE, "AI assistant is temporarily unavailable"),
                request);

        assertThat(response.getBody().code()).isEqualTo("AI_UNAVAILABLE");
        assertThat(handler.handleApi(ApiException.notFound(ErrorCodes.INVITE_INVALID, "Курс не найден"), request)
                .getBody().code()).isEqualTo("INVITE_INVALID");
    }

    @Test
    void anApiExceptionCannotBeCreatedWithoutACode() {
        assertThatThrownBy(() -> new ApiException(HttpStatus.BAD_REQUEST, "no code", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void frameworkErrorsGetCodesToo() {
        assertThat(handler.handleAccessDenied(new AccessDeniedException("x"), request).getBody().code())
                .isEqualTo(ErrorCodes.ACCESS_DENIED);
        assertThat(handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1), request).getBody().code())
                .isEqualTo(ErrorCodes.UPLOAD_TOO_LARGE);
        ResponseEntity<ErrorResponse> unexpected = handler.handleUnexpected(new IllegalStateException("boom"), request);
        assertThat(unexpected.getBody().code()).isEqualTo(ErrorCodes.INTERNAL_ERROR);
        assertThat(unexpected.getBody().message()).isEqualTo("Internal server error"); // No internals leak.
    }

    @Test
    void securityFilterErrorsCarryACode() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestSecurityErrorHandler(json).commence(request, response, new InsufficientAuthenticationException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(json.readTree(response.getContentAsByteArray()).get("code").asText())
                .isEqualTo(ErrorCodes.AUTHENTICATION_REQUIRED);
    }
}
