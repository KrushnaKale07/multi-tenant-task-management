package com.techpulse.taskmanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid e-mail address")
        String email,

        @NotBlank(message = "password is required")
        String password) {
}
