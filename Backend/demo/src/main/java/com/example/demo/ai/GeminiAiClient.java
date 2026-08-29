package com.example.demo.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini (Google AI Studio) backend, using the current <b>Interactions API</b>
 * ({@code POST /v1beta/interactions}) — the default interface as of June 2026,
 * which supersedes the legacy {@code :generateContent} endpoint.
 *
 * <p>This is the default provider (see {@code app.ai.provider}). The API key is
 * read from {@code app.ai.gemini.api-key} (backed by the {@code GEMINI_API_KEY}
 * environment variable / .env) and sent in the {@code x-goog-api-key} header so
 * it never appears in the URL or logs. Configured via {@code app.ai.gemini.*}.
 *
 * <p>Requests are stateless: the full system prompt and conversation are sent on
 * every call (no {@code previous_interaction_id}), matching the {@link AiClient}
 * contract so callers need no change.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiAiClient implements AiClient {

    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final Duration requestTimeout;

    public GeminiAiClient(
            ObjectMapper objectMapper,
            @Value("${app.ai.gemini.api-key:}") String apiKey,
            @Value("${app.ai.gemini.model:gemini-3.7-flash}") String model,
            @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${app.ai.gemini.timeout-seconds:60}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
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
     * Shared request path: maps the turns to Interactions {@code input} steps
     * (role "assistant" becomes a "model_output" step, everything else a
     * "user_input" step), puts the system prompt in {@code system_instruction},
     * and returns the concatenated text of the response's model output.
     */
    private String send(String systemPrompt, List<ChatMessage> turns) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiUnavailableException(
                    "Gemini API key not configured (set GEMINI_API_KEY)", null);
        }
        try {
            List<Map<String, Object>> input = new ArrayList<>();
            for (ChatMessage turn : turns) {
                String type = "assistant".equals(turn.role()) ? "model_output" : "user_input";
                input.add(step(type, turn.content()));
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                body.put("system_instruction", systemPrompt);
            }
            body.put("input", input);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/interactions"))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(requestTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new AiUnavailableException(
                        "Gemini returned HTTP " + response.statusCode() + ": "
                                + truncate(response.body()), null);
            }

            return extractText(response.body());

        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AiUnavailableException("Gemini request failed", e);
        }
    }

    /** Builds one Interactions input step: {@code {type, content:[{type:text, text}]}}. */
    private static Map<String, Object> step(String type, String text) {
        return Map.of(
                "type", type,
                "content", List.of(Map.of("type", "text", "text", text)));
    }

    /**
     * Pulls the model's text out of an Interactions response: prefers the
     * SDK-style {@code output_text} shortcut, otherwise concatenates the text
     * parts of every {@code model_output} step.
     */
    private String extractText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        StringBuilder text = new StringBuilder();
        JsonNode steps = root.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                if (!"model_output".equals(step.path("type").asText())) {
                    continue;
                }
                for (JsonNode part : step.path("content")) {
                    if ("text".equals(part.path("type").asText())) {
                        text.append(part.path("text").asText(""));
                    }
                }
            }
        }

        if (text.toString().isBlank()) {
            String status = root.path("status").asText("");
            throw new AiUnavailableException("Gemini returned an empty response"
                    + (status.isBlank() ? "" : " (status: " + status + ")"), null);
        }
        return text.toString();
    }

    /** Keeps error messages readable when the API returns a large body. */
    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500) + "…";
    }
}
