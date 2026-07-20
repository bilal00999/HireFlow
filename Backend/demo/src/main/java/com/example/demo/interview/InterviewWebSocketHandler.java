package com.example.demo.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the WebSocket at {@code /ws/interview/{token}} to the transport-
 * agnostic {@link InterviewService}. The token is taken from the connection URI
 * (last path segment) and reused for every frame on that connection.
 *
 * <p>Wire protocol (JSON text frames), per 04-API-DESIGN.md:
 * <pre>
 *   client -&gt; server: { "type": "START" }
 *                     { "type": "ANSWER", "content": "..." }
 *                     { "type": "END" }
 *   server -&gt; client: { "type": "QUESTION"|"COMPLETE"|"ERROR", "content": "..." }
 * </pre>
 *
 * <p>The service holds all conversation state in the database, so a single
 * handler instance safely serves every connection without per-session fields.
 */
@Component
public class InterviewWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(InterviewWebSocketHandler.class);

    private final InterviewService interviewService;
    private final ObjectMapper objectMapper;

    public InterviewWebSocketHandler(InterviewService interviewService, ObjectMapper objectMapper) {
        this.interviewService = interviewService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String token = tokenFrom(session);
        if (token == null || token.isBlank()) {
            send(session, InterviewEvent.error("Missing interview token."));
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        String type;
        String content;
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            type = node.path("type").asText("");
            content = node.path("content").asText(null);
        } catch (Exception e) {
            send(session, InterviewEvent.error("Malformed message."));
            return;
        }

        try {
            switch (type) {
                case "START" -> send(session, interviewService.start(token));
                case "ANSWER" -> {
                    InterviewEvent event = interviewService.answer(token, content);
                    send(session, event);
                    if (event.type() == InterviewEvent.Type.COMPLETE) {
                        session.close(CloseStatus.NORMAL);
                    }
                }
                case "END" -> {
                    send(session, InterviewEvent.complete(
                            "Interview ended. Thank you for your time."));
                    session.close(CloseStatus.NORMAL);
                }
                default -> send(session, InterviewEvent.error("Unknown message type: " + type));
            }
        } catch (Exception e) {
            // Business/validation errors (bad token, empty answer, AI down) come
            // back as an ERROR frame rather than dropping the socket abruptly.
            log.warn("Interview message handling failed", e);
            send(session, InterviewEvent.error(e.getMessage() != null
                    ? e.getMessage() : "Something went wrong."));
        }
    }

    private void send(WebSocketSession session, InterviewEvent event) throws Exception {
        // HashMap (not Map.of) so a null content never triggers an NPE.
        Map<String, String> payload = new HashMap<>();
        payload.put("type", event.type().name());
        payload.put("content", event.content() != null ? event.content() : "");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    /** Token is the last segment of the connection path: /ws/interview/{token}. */
    private String tokenFrom(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : null;
    }
}
