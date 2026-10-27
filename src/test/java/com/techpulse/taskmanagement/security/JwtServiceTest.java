package com.techpulse.taskmanagement.security;

import com.techpulse.taskmanagement.config.JwtProperties;
import com.techpulse.taskmanagement.entity.Role;
import com.techpulse.taskmanagement.entity.User;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-unit-test-secret-1234";

    private final User manager = User.builder()
            .id(5L).email("manager@acme.com").role(Role.MANAGER).tenantId(7L).build();

    @Test
    void generatedTokenRoundTripsTenantAndRole() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));

        AuthenticatedUser parsed = service.parseToken(service.generateToken(manager));

        assertThat(parsed.userId()).isEqualTo(5L);
        assertThat(parsed.tenantId()).isEqualTo(7L);
        assertThat(parsed.role()).isEqualTo(Role.MANAGER);
        assertThat(parsed.email()).isEqualTo("manager@acme.com");
        assertThat(service.getExpirationSeconds()).isEqualTo(3600);
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));
        String token = service.generateToken(manager);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThatThrownBy(() -> service.parseToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));
        JwtService other = new JwtService(new JwtProperties("another-secret-another-secret-another-secret-9999", 60));

        assertThatThrownBy(() -> service.parseToken(other.generateToken(manager))).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService expiring = new JwtService(new JwtProperties(SECRET, -1));
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));

        assertThatThrownBy(() -> service.parseToken(expiring.generateToken(manager)))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenWithoutTenantClaim() {
        JwtService service = new JwtService(new JwtProperties(SECRET, 60));
        String noTenant = Jwts.builder()
                .subject("x@y.com")
                .claim(JwtService.CLAIM_USER_ID, 1)
                .claim(JwtService.CLAIM_ROLE, "USER")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> service.parseToken(noTenant)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWeakSecret() {
        JwtProperties weak = new JwtProperties("short", 60);
        assertThatThrownBy(() -> new JwtService(weak)).isInstanceOf(WeakKeyException.class);
    }
}
