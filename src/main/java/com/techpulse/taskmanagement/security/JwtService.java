package com.techpulse.taskmanagement.security;

import com.techpulse.taskmanagement.config.JwtProperties;
import com.techpulse.taskmanagement.entity.Role;
import com.techpulse.taskmanagement.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;


@Service
public class JwtService {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USER_ID = "user_id";

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(properties.expirationMinutes());
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_TENANT_ID, user.getTenantId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(signingKey)
                .compact();
    }

    
    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        Number tenantId = claims.get(CLAIM_TENANT_ID, Number.class);
        Number userId = claims.get(CLAIM_USER_ID, Number.class);
        String role = claims.get(CLAIM_ROLE, String.class);
        if (tenantId == null || userId == null || role == null) {
            throw new MalformedJwtException("Token is missing required claims");
        }
        return new AuthenticatedUser(userId.longValue(), claims.getSubject(), tenantId.longValue(), Role.valueOf(role));
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
