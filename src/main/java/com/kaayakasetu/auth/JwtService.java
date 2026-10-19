package com.kaayakasetu.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates and checks JSON Web Tokens.
 * A JWT is three Base64 parts: header.payload.signature. The payload carries the user id and an expiry;
 * the signature is an HMAC of header+payload made with our secret key. Anyone can READ the payload,
 * but nobody can change it without breaking the signature - so the server can trust a valid token
 * without keeping any session in memory (stateless).
 */
@Service
public class JwtService {

    private static final String ISSUER = "kaayakasetu";

    private final SecretKey key;
    private final long expirySeconds;

    public JwtService(@Value("${app.security.jwt-secret}") String secret,
                      @Value("${app.security.jwt-expiry-minutes:120}") long expiryMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.security.jwt-secret (JWT_SECRET) must be at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirySeconds = expiryMinutes * 60;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.id()))
                .claim("role", user.role().name())
                .claim("phone", user.phone())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(key)
                .compact();
    }

    /** Returns the user id inside a valid token; empty for a forged, tampered or expired token. */
    public Optional<Long> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.parseLong(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long expirySeconds() {
        return expirySeconds;
    }
}
