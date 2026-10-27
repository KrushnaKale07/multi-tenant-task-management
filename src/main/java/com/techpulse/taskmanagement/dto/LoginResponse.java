package com.techpulse.taskmanagement.dto;

import com.techpulse.taskmanagement.entity.Role;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Long tenantId,
        Role role) {
}
