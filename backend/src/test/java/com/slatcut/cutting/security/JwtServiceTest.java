package com.slatcut.cutting.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtProperties propertiesWithExpiration(long expirationMs) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-key-at-least-256-bits-long-0123456789abcdef");
        properties.setExpirationMs(expirationMs);
        return properties;
    }

    @Test
    void generateAndParseToken_roundTripsUsernameAndRole() {
        JwtService service = new JwtService(propertiesWithExpiration(60_000));

        String token = service.generateToken("admin", "ADMIN");

        assertThat(service.isValid(token)).isTrue();
        assertThat(service.extractUsername(token)).isEqualTo("admin");
        assertThat(service.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void isValid_returnsFalseForExpiredToken() throws InterruptedException {
        JwtService service = new JwtService(propertiesWithExpiration(1));

        String token = service.generateToken("admin", "ADMIN");
        Thread.sleep(10);

        assertThat(service.isValid(token)).isFalse();
    }

    @Test
    void isValid_returnsFalseForTamperedToken() {
        JwtService service = new JwtService(propertiesWithExpiration(60_000));
        String token = service.generateToken("admin", "ADMIN");

        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertThat(service.isValid(tampered)).isFalse();
    }

    @Test
    void isValid_returnsFalseForTokenSignedWithDifferentSecret() {
        JwtService issuer = new JwtService(propertiesWithExpiration(60_000));
        String token = issuer.generateToken("admin", "ADMIN");

        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-completely-different-secret-key-0123456789abcdef-xyz");
        otherProperties.setExpirationMs(60_000);
        JwtService verifier = new JwtService(otherProperties);

        assertThat(verifier.isValid(token)).isFalse();
    }
}
