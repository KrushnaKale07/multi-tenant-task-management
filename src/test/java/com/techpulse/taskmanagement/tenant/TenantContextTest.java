package com.techpulse.taskmanagement.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void storesAndClearsTenant() {
        TenantContext.setTenantId(5L);
        assertThat(TenantContext.getTenantId()).isEqualTo(5L);
        assertThat(TenantContext.requireTenantId()).isEqualTo(5L);

        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void requireTenantFailsWhenMissing() {
        assertThatThrownBy(TenantContext::requireTenantId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void tenantIsNotVisibleToOtherThreads() {
        TenantContext.setTenantId(5L);
        Long seenByOtherThread = CompletableFuture.supplyAsync(TenantContext::getTenantId).join();
        assertThat(seenByOtherThread).isNull();
    }
}
