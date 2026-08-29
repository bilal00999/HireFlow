package com.example.demo.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live, opt-in diagnostic that calls the real Gemini Interactions API to confirm
 * the configured key works end-to-end. It is skipped during normal builds and
 * only runs when explicitly enabled:
 *
 * <pre>./mvnw test -Dgemini.diagnostic=true -Dtest=GeminiDiagnosticTest</pre>
 *
 * It confirms three things the task asked for:
 *   1. the API key is loaded (from the environment / .env),
 *   2. the request reaches Gemini,
 *   3. Gemini returns a valid, non-blank text response.
 *
 * The key is resolved from the Spring environment first (spring-dotenv / OS env,
 * exactly as the running app does), then the OS environment, and finally the
 * {@code .env} file directly — so the diagnostic works regardless of how the
 * test JVM's working directory is set. The key itself is NEVER printed; only its
 * presence, source, and length are reported.
 */
@SpringBootTest(properties = "spring.config.import=optional:file:.env[.properties]")
@EnabledIfSystemProperty(named = "gemini.diagnostic", matches = "true")
class GeminiDiagnosticTest {

    @Autowired
    ObjectMapper objectMapper;

    @Value("${GEMINI_API_KEY:}")
    String apiKeyFromSpring;

    @Value("${app.ai.gemini.model:gemini-3.7-flash}")
    String model;

    @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    String baseUrl;

    @Test
    void geminiReturnsText() {
        // (1) resolve the key from whichever source has it — never print the value.
        String cwd = System.getProperty("user.dir");
        System.out.println("[gemini-diagnostic] user.dir=" + cwd);

        String source;
        String apiKey;
        if (apiKeyFromSpring != null && !apiKeyFromSpring.isBlank()) {
            source = "spring-environment (spring-dotenv/OS)";
            apiKey = apiKeyFromSpring.trim();
        } else if (System.getenv("GEMINI_API_KEY") != null
                && !System.getenv("GEMINI_API_KEY").isBlank()) {
            source = "OS environment";
            apiKey = System.getenv("GEMINI_API_KEY").trim();
        } else {
            Path envFile = locateEnvFile(cwd);
            System.out.println("[gemini-diagnostic] .env file: "
                    + (envFile != null ? envFile.toAbsolutePath() : "not found"));
            apiKey = envFile != null ? readKeyFromEnv(envFile) : "";
            source = ".env file (direct read)";
        }

        assertThat(apiKey)
                .as("GEMINI_API_KEY must be resolvable (Spring env, OS env, or .env) to run this diagnostic")
                .isNotBlank();
        System.out.println("[gemini-diagnostic] API key loaded: yes"
                + " (source=" + source + ", length=" + apiKey.length() + ")");
        System.out.println("[gemini-diagnostic] model=" + model
                + " endpoint=" + baseUrl + "/interactions");

        // (2) request reaches Gemini, (3) a valid text response comes back.
        GeminiAiClient client = new GeminiAiClient(objectMapper, apiKey, model, baseUrl, 60);
        String reply = client.complete(
                "You are a connectivity diagnostic. Answer with a single word.",
                "Reply with the single word: OK");

        System.out.println("[gemini-diagnostic] response length=" + reply.length());
        System.out.println("[gemini-diagnostic] response=" + reply.strip());
        assertThat(reply).isNotBlank();
    }

    /** Walks up from the working dir looking for a {@code .env}, so the test is cwd-agnostic. */
    private static Path locateEnvFile(String startDir) {
        Path dir = Paths.get(startDir).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            Path nested = dir.resolve("Backend").resolve("demo").resolve(".env");
            if (Files.isRegularFile(nested)) {
                return nested;
            }
        }
        return null;
    }

    /** Reads GEMINI_API_KEY from a .env file without logging its value. */
    private static String readKeyFromEnv(Path envFile) {
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || !trimmed.startsWith("GEMINI_API_KEY=")) {
                    continue;
                }
                String value = trimmed.substring("GEMINI_API_KEY=".length()).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        } catch (Exception e) {
            System.out.println("[gemini-diagnostic] failed to read .env: " + e.getMessage());
        }
        return "";
    }
}
