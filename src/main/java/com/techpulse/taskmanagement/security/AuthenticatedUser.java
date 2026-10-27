package com.techpulse.taskmanagement.security;

import com.techpulse.taskmanagement.entity.Role;


public record AuthenticatedUser(Long userId, String email, Long tenantId, Role role) {
}
