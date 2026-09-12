package com.algolens.security;

import com.algolens.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Issues and verifies the HS256 access tokens the SPA sends on every request. */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_NAME = "name";

    private final SecretKey key;
    private final String issuer;
    private final Duration validity;

    public JwtService(AppProperties properties) {
        AppProperties.Jwt config = properties.jwt();
        byte[] secret = config.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            // Fail at startup rather than issuing tokens that are cheap to forge.
            throw new IllegalStateException(
                    "algolens.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256 (set ALGOLENS_JWT_SECRET)");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = config.issuer();
        this.validity = Duration.ofMinutes(config.expiryMinutes());
    }

    public String issue(Long userId, String email, String name) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuer(issuer)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_NAME, name)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    public long expiresInSeconds() {
        return validity.toSeconds();
    }

    /** Returns the token's claims, or empty when the token is missing, expired or tampered. */
    public Optional<Claims> verify(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            // Expected for expired or forged tokens: debug, not warn, or logs fill with noise.
            log.debug("Rejected a JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public static Long userIdOf(Claims claims) {
        Object raw = claims.get(CLAIM_USER_ID);
        return raw instanceof Number number ? number.longValue() : null;
    }

    public static String nameOf(Claims claims) {
        Object raw = claims.get(CLAIM_NAME);
        return raw == null ? null : String.valueOf(raw);
    }
}
