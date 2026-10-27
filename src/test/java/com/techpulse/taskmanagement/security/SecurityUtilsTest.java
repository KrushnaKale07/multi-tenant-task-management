package com.techpulse.taskmanagement.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.techpulse.taskmanagement.entity.Role;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsAuthenticatedPrincipal() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "a@b.com", 2L, Role.ADMIN);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));

        assertThat(SecurityUtils.currentUser()).isEqualTo(user);
    }

    @Test
    void failsWithoutAuthentication() {
        assertThatThrownBy(SecurityUtils::currentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void failsWhenPrincipalIsNotAnAuthenticatedUser() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("anonymous", null, List.of()));

        assertThatThrownBy(SecurityUtils::currentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
