package com.techpulse.taskmanagement.dto;

import java.time.Instant;

import com.techpulse.taskmanagement.entity.Role;
import com.techpulse.taskmanagement.entity.User;

public record UserResponse(Long id, String email, Role role, Long tenantId, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getTenantId(), user.getCreatedAt());
    }
}
