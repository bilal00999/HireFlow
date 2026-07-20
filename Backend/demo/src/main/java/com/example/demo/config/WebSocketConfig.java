package com.example.demo.config;

import com.example.demo.interview.InterviewWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the AI interview WebSocket at {@code /ws/interview/{token}}. The
 * token is read from the path by {@link InterviewWebSocketHandler}. Origins are
 * limited to the dev frontends (same list as the CORS config); tighten for
 * production. The endpoint is left off the HTTP security chain because the
 * WebSocket handshake path is separate from {@code /api/**} and gating happens
 * on the token inside the handler.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final InterviewWebSocketHandler interviewHandler;

    public WebSocketConfig(InterviewWebSocketHandler interviewHandler) {
        this.interviewHandler = interviewHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(interviewHandler, "/ws/interview/{token}")
                .setAllowedOrigins("http://localhost:5173", "http://localhost:3000");
    }
}
