package com.techpulse.taskmanagement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignTaskRequest(
        @NotNull(message = "userId is required")
        @Positive(message = "userId must be a positive number")
        Long userId) {
}
