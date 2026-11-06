package com.agent.gateway.tenant;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * HS256 JWT 签发与校验（access / refresh）。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public JwtService(
            @Value("${gateway.auth.jwt-secret:dev-only-change-me-please-32bytes-min}") String secret,
            @Value("${gateway.auth.access-ttl-seconds:3600}") long accessTtlSeconds,
            @Value("${gateway.auth.refresh-ttl-seconds:1209600}") long refreshTtlSeconds) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 需要足够密钥材料
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.length, 32));
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String issueAccessToken(String userId, String email) {
        return issue(userId, email, "access", accessTtlSeconds);
    }

    public String issueRefreshToken(String userId, String email) {
        return issue(userId, email, "refresh", refreshTtlSeconds);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String requireUserId(String token, String expectedType) {
        Claims claims = parse(token);
        String typ = claims.get("typ", String.class);
        if (expectedType != null && !expectedType.equals(typ)) {
            throw new UnauthorizedException("Invalid token type");
        }
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new UnauthorizedException("Invalid token subject");
        }
        return sub;
    }

    private String issue(String userId, String email, String typ, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("typ", typ)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }
}
