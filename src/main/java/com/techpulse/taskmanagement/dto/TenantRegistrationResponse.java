package com.techpulse.taskmanagement.dto;

import java.time.Instant;

public record TenantRegistrationResponse(
        Long tenantId,
        String tenantName,
        Long adminUserId,
        String adminEmail,
        Instant createdAt) {
}
