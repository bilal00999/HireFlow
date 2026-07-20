package com.example.demo.token;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates the single-use, expiring tokens that gate the assessment
 * and interview stages. Token values are 256 bits of {@link SecureRandom},
 * URL-safe Base64 encoded, so they are effectively unguessable.
 */
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 32; // 256-bit

    private final TokenRepository tokenRepository;

    public TokenService(TokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Creates a new token for an application, valid for {@code ttl} from now.
     */
    @Transactional
    public Token create(UUID applicationId, TokenType type, Duration ttl) {
        Token token = new Token();
        token.setTokenValue(generateValue());
        token.setApplicationId(applicationId);
        token.setTokenType(type.name());
        token.setExpiresAt(LocalDateTime.now().plus(ttl));
        return tokenRepository.save(token);
    }

    /**
     * Validates a raw token value: it must exist, match the expected type, be
     * unused, and not be expired. Does not consume the token — call
     * {@link #markUsed} once the gated action actually starts.
     */
    @Transactional(readOnly = true)
    public TokenValidation validate(String tokenValue, TokenType expectedType) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return TokenValidation.invalid(TokenStatus.NOT_FOUND);
        }
        Token token = tokenRepository.findByTokenValue(tokenValue).orElse(null);
        if (token == null || !token.getTokenType().equals(expectedType.name())) {
            return TokenValidation.invalid(TokenStatus.NOT_FOUND);
        }
        if (token.getUsedAt() != null) {
            return TokenValidation.invalid(TokenStatus.ALREADY_USED);
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return TokenValidation.invalid(TokenStatus.EXPIRED);
        }
        return TokenValidation.valid(token);
    }

    /**
     * Looks up a token by value and type, ignoring used/expired status. Returns
     * empty for a blank value, an unknown value, or a type mismatch. Use this to
     * resume a stateful flow (e.g. an in-progress interview) whose gating token
     * was already consumed at start — the flow's own state is the real guard.
     */
    @Transactional(readOnly = true)
    public Optional<Token> findByValueAndType(String tokenValue, TokenType expectedType) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findByTokenValue(tokenValue)
                .filter(t -> t.getTokenType().equals(expectedType.name()));
    }

    /**
     * Marks a token consumed so its link cannot be reused. Idempotent-safe:
     * the first call wins and later calls leave the original timestamp intact.
     */
    @Transactional
    public void markUsed(Token token) {
        if (token.getUsedAt() == null) {
            token.setUsedAt(LocalDateTime.now());
            tokenRepository.save(token);
        }
    }

    private String generateValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
