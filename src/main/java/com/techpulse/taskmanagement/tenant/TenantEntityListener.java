package com.techpulse.taskmanagement.tenant;

import com.techpulse.taskmanagement.exception.TenantViolationException;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class TenantEntityListener {

    @PrePersist
    public void validateOnPersist(Object entity) {
        validate(entity);
    }

    @PreUpdate
    public void validateOnUpdate(Object entity) {
        validate(entity);
    }

    private void validate(Object entity) {
        Long currentTenant = TenantContext.getTenantId();
        if (currentTenant != null && entity instanceof TenantOwned owned
                && !currentTenant.equals(owned.getTenantId())) {
            throw new TenantViolationException("Cross-tenant write blocked");
        }
    }
}
