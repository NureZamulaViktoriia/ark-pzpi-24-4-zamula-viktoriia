package com.rackguard.rackguardserver.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.ttl-hours}") int ttlHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlHours * 3_600_000L;
    }

    public long ttlSeconds() { return ttlMillis / 1000; }

    public String generate(Integer userId, String role) {
        Date now = new Date();
        return Jwts.builder().subject(String.valueOf(userId)).claim("role", role)
                .issuedAt(now).expiration(new Date(now.getTime() + ttlMillis)).signWith(key).compact();
    }

    public Optional<Auth.UserPrincipal> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new Auth.UserPrincipal(Integer.valueOf(c.getSubject()), c.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
