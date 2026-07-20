package com.example.demo.ai;

/**
 * One turn in a multi-turn AI conversation. {@code role} is an Ollama/OpenAI
 * chat role — "system", "user", or "assistant". Used by {@link AiClient#chat}
 * so features like the interview can carry full conversation history.
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
