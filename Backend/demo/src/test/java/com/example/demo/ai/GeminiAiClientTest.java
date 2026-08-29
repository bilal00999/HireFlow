package com.example.demo.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hermetic tests for {@link GeminiAiClient}: a local stub HTTP server stands in
 * for the Gemini Interactions API, so these verify request shaping and response
 * parsing without any network access. The live round-trip lives separately in
 * {@code GeminiDiagnosticTest}.
 */
class GeminiAiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;

    private volatile String lastRequestBody;
    private volatile int responseStatus = 200;
    private volatile String responseBody = modelOutput("OK");

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1beta/interactions", exchange -> {
            lastRequestBody = new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1beta";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private GeminiAiClient client(String apiKey) {
        return new GeminiAiClient(mapper, apiKey, "gemini-3.7-flash", baseUrl, 10);
    }

    private static String modelOutput(String text) {
        return "{\"object\":\"interaction\",\"status\":\"completed\",\"steps\":["
                + "{\"type\":\"model_output\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"" + text + "\"}]}]}";
    }

    @Test
    void complete_sendsInteractionsRequestAndParsesModelOutput() throws Exception {
        responseBody = modelOutput("Hello there");

        String reply = client("test-key").complete("You are terse.", "Say hi");

        assertThat(reply).isEqualTo("Hello there");

        JsonNode sent = mapper.readTree(lastRequestBody);
        assertThat(sent.path("model").asText()).isEqualTo("gemini-3.7-flash");
        assertThat(sent.path("system_instruction").asText()).isEqualTo("You are terse.");
        JsonNode input = sent.path("input");
        assertThat(input.isArray()).isTrue();
        assertThat(input.get(0).path("type").asText()).isEqualTo("user_input");
        assertThat(input.get(0).path("content").get(0).path("type").asText()).isEqualTo("text");
        assertThat(input.get(0).path("content").get(0).path("text").asText()).isEqualTo("Say hi");
    }

    @Test
    void chat_mapsAssistantRoleToModelOutputStep() throws Exception {
        client("test-key").chat("sys", List.of(
                ChatMessage.user("hi"),
                ChatMessage.assistant("hello"),
                ChatMessage.user("tell me more")));

        JsonNode input = mapper.readTree(lastRequestBody).path("input");
        assertThat(input.get(0).path("type").asText()).isEqualTo("user_input");
        assertThat(input.get(1).path("type").asText()).isEqualTo("model_output");
        assertThat(input.get(2).path("type").asText()).isEqualTo("user_input");
        assertThat(input.get(1).path("content").get(0).path("text").asText()).isEqualTo("hello");
    }

    @Test
    void omitsSystemInstructionWhenBlank() throws Exception {
        client("test-key").complete("   ", "hi");
        JsonNode sent = mapper.readTree(lastRequestBody);
        assertThat(sent.has("system_instruction")).isFalse();
    }

    @Test
    void blankApiKey_throwsWithoutCallingServer() {
        assertThatThrownBy(() -> client("").complete("s", "u"))
                .isInstanceOf(AiUnavailableException.class);
        assertThat(lastRequestBody).isNull();
    }

    @Test
    void httpError_throwsAiUnavailable() {
        responseStatus = 400;
        responseBody = "{\"error\":{\"message\":\"bad request\"}}";
        assertThatThrownBy(() -> client("test-key").complete("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("400");
    }

    @Test
    void emptyModelOutput_throwsAiUnavailable() {
        responseBody = "{\"status\":\"completed\",\"steps\":[]}";
        assertThatThrownBy(() -> client("test-key").complete("s", "u"))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("empty");
    }
}
