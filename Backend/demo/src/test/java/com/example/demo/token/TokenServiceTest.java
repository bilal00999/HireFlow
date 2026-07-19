package com.example.demo.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TokenService}: value generation, and the create /
 * validate / markUsed lifecycle including expiry and single-use enforcement.
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock TokenRepository tokenRepository;

    TokenService tokenService;
    UUID applicationId;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(tokenRepository);
        applicationId = UUID.randomUUID();
    }

    @Test
    void create_generatesUrlSafeValueAndFutureExpiry() {
        when(tokenRepository.save(any(Token.class))).thenAnswer(inv -> inv.getArgument(0));

        Token token = tokenService.create(applicationId, TokenType.ASSESSMENT, Duration.ofHours(24));

        assertThat(token.getApplicationId()).isEqualTo(applicationId);
        assertThat(token.getTokenType()).isEqualTo("ASSESSMENT");
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
        // URL-safe Base64 without padding: no +, /, or = characters.
        assertThat(token.getTokenValue()).isNotBlank();
        assertThat(token.getTokenValue()).doesNotContain("+", "/", "=");
    }

    @Test
    void create_generatesUniqueValues() {
        when(tokenRepository.save(any(Token.class))).thenAnswer(inv -> inv.getArgument(0));

        Token a = tokenService.create(applicationId, TokenType.ASSESSMENT, Duration.ofHours(1));
        Token b = tokenService.create(applicationId, TokenType.ASSESSMENT, Duration.ofHours(1));

        assertThat(a.getTokenValue()).isNotEqualTo(b.getTokenValue());
    }

    @Test
    void validate_validToken_returnsValid() {
        Token token = token(TokenType.ASSESSMENT, LocalDateTime.now().plusHours(1), null);
        when(tokenRepository.findByTokenValue("abc")).thenReturn(Optional.of(token));

        TokenValidation result = tokenService.validate("abc", TokenType.ASSESSMENT);

        assertThat(result.isValid()).isTrue();
        assertThat(result.status()).isEqualTo(TokenStatus.VALID);
        assertThat(result.token()).isSameAs(token);
    }

    @Test
    void validate_unknownValue_returnsNotFound() {
        when(tokenRepository.findByTokenValue("nope")).thenReturn(Optional.empty());

        TokenValidation result = tokenService.validate("nope", TokenType.ASSESSMENT);

        assertThat(result.status()).isEqualTo(TokenStatus.NOT_FOUND);
        assertThat(result.token()).isNull();
    }

    @Test
    void validate_blankValue_returnsNotFoundWithoutHittingRepo() {
        TokenValidation result = tokenService.validate("  ", TokenType.ASSESSMENT);

        assertThat(result.status()).isEqualTo(TokenStatus.NOT_FOUND);
        verify(tokenRepository, never()).findByTokenValue(any());
    }

    @Test
    void validate_wrongType_returnsNotFound() {
        Token token = token(TokenType.INTERVIEW, LocalDateTime.now().plusHours(1), null);
        when(tokenRepository.findByTokenValue("abc")).thenReturn(Optional.of(token));

        TokenValidation result = tokenService.validate("abc", TokenType.ASSESSMENT);

        assertThat(result.status()).isEqualTo(TokenStatus.NOT_FOUND);
    }

    @Test
    void validate_expiredToken_returnsExpired() {
        Token token = token(TokenType.ASSESSMENT, LocalDateTime.now().minusMinutes(1), null);
        when(tokenRepository.findByTokenValue("abc")).thenReturn(Optional.of(token));

        TokenValidation result = tokenService.validate("abc", TokenType.ASSESSMENT);

        assertThat(result.status()).isEqualTo(TokenStatus.EXPIRED);
    }

    @Test
    void validate_usedToken_returnsAlreadyUsed() {
        Token token = token(TokenType.ASSESSMENT,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().minusMinutes(5));
        when(tokenRepository.findByTokenValue("abc")).thenReturn(Optional.of(token));

        TokenValidation result = tokenService.validate("abc", TokenType.ASSESSMENT);

        assertThat(result.status()).isEqualTo(TokenStatus.ALREADY_USED);
    }

    @Test
    void markUsed_setsTimestampOnce() {
        Token token = token(TokenType.ASSESSMENT, LocalDateTime.now().plusHours(1), null);

        tokenService.markUsed(token);

        assertThat(token.getUsedAt()).isNotNull();
        LocalDateTime firstUse = token.getUsedAt();

        // Second call is a no-op and preserves the original timestamp.
        tokenService.markUsed(token);
        assertThat(token.getUsedAt()).isEqualTo(firstUse);

        // save invoked only on the first, effective call.
        ArgumentCaptor<Token> captor = ArgumentCaptor.forClass(Token.class);
        verify(tokenRepository).save(captor.capture());
    }

    private Token token(TokenType type, LocalDateTime expiresAt, LocalDateTime usedAt) {
        Token t = new Token();
        t.setTokenValue("abc");
        t.setApplicationId(applicationId);
        t.setTokenType(type.name());
        t.setExpiresAt(expiresAt);
        t.setUsedAt(usedAt);
        return t;
    }
}
