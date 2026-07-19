package com.example.demo.token;

import java.util.Optional;

/**
 * Result of {@link TokenService#validate}: a status plus, when valid, the token
 * itself so callers can read its application id and mark it used.
 */
public record TokenValidation(TokenStatus status, Token token) {

    public boolean isValid() {
        return status == TokenStatus.VALID;
    }

    public static TokenValidation valid(Token token) {
        return new TokenValidation(TokenStatus.VALID, token);
    }

    public static TokenValidation invalid(TokenStatus status) {
        return new TokenValidation(status, null);
    }

    public Optional<Token> tokenIfValid() {
        return isValid() ? Optional.of(token) : Optional.empty();
    }
}
