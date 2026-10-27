package com.techpulse.taskmanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterTenantRequest(
        @NotBlank(message = "tenantName is required")
        @Size(min = 2, max = 100, message = "tenantName must be 2-100 characters")
        String tenantName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid e-mail address")
        @Size(max = 150, message = "email must be at most 150 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String password) {
}
