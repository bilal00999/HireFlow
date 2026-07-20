package com.example.demo.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Free, local AI backend (Ollama, http://localhost:11434). Sends a chat
 * request with {@code stream:false} and returns the assembled message text.
 *
 * <p>Configured via {@code app.ai.ollama.*}. This is the default provider for
 * development; production can swap in a Gemini-backed {@link AiClient}.
 */
@Component
public class OllamaAiClient implements AiClient {

    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final Duration requestTimeout;

    public OllamaAiClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.ai.ollama.model:mistral}") String model,
            @Value("${app.ai.ollama.timeout-seconds:120}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.model = model;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 15)))
                .build();
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return send(systemPrompt, List.of(new ChatMessage("user", userPrompt)));
    }

    @Override
    public String chat(String systemPrompt, List<ChatMessage> history) {
        return send(systemPrompt, history);
    }

    /**
     * Shared request path: prepends the system prompt to the given turns, calls
     * Ollama with {@code stream:false}, and returns the assembled reply text.
     */
    private String send(String systemPrompt, List<ChatMessage> turns) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            for (ChatMessage turn : turns) {
                messages.add(Map.of("role", turn.role(), "content", turn.content()));
            }

            Map<String, Object> body = Map.of(
                    "model", model,
                    "stream", false,
                    "messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new AiUnavailableException(
                        "Ollama returned HTTP " + response.statusCode(), null);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new AiUnavailableException("Ollama returned an empty response", null);
            }
            return content.asText();

        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AiUnavailableException("Ollama request failed", e);
        }
    }
}
