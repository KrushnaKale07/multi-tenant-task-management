package com.techpulse.taskmanagement.tenant;

import com.techpulse.taskmanagement.entity.Task;
import com.techpulse.taskmanagement.exception.TenantViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantEntityListenerTest {

    private final TenantEntityListener listener = new TenantEntityListener();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void allowsWriteOfOwnTenant() {
        TenantContext.setTenantId(1L);
        Task task = Task.builder().tenantId(1L).build();
        assertThatCode(() -> listener.validateOnPersist(task)).doesNotThrowAnyException();
        assertThatCode(() -> listener.validateOnUpdate(task)).doesNotThrowAnyException();
    }

    @Test
    void blocksWriteOfAnotherTenant() {
        TenantContext.setTenantId(1L);
        Task foreign = Task.builder().tenantId(2L).build();
        assertThatThrownBy(() -> listener.validateOnPersist(foreign)).isInstanceOf(TenantViolationException.class);
        assertThatThrownBy(() -> listener.validateOnUpdate(foreign)).isInstanceOf(TenantViolationException.class);
    }

    @Test
    void ignoresWritesWithoutTenantContextAndNonTenantEntities() {
        Task task = Task.builder().tenantId(2L).build();
        assertThatCode(() -> listener.validateOnPersist(task)).doesNotThrowAnyException();

        TenantContext.setTenantId(1L);
        assertThatCode(() -> listener.validateOnPersist(new Object())).doesNotThrowAnyException();
    }
}
