package com.example.demo.ai;

import java.util.List;

/**
 * Abstraction over the LLM backend. Implementations talk to a specific
 * provider (Ollama locally, Gemini in production) but callers only ever
 * send prompts and get raw text back. This keeps the ATS, assessment, and
 * interview features free of vendor lock-in.
 */
public interface AiClient {

    /**
     * Sends the prompt to the model and returns its raw text response.
     *
     * @param systemPrompt role/behaviour instructions for the model
     * @param userPrompt   the actual task content
     * @return the model's raw text output (callers parse JSON as needed)
     * @throws AiUnavailableException if the provider cannot be reached or errors
     */
    String complete(String systemPrompt, String userPrompt);

    /**
     * Multi-turn variant used by the AI interview: the model sees the system
     * prompt plus the full conversation so far and returns its next reply.
     *
     * @param systemPrompt role/behaviour instructions for the model
     * @param history      the conversation so far, oldest first
     * @return the model's raw text output for the next turn
     * @throws AiUnavailableException if the provider cannot be reached or errors
     */
    String chat(String systemPrompt, List<ChatMessage> history);
}
