package com.techpulse.taskmanagement.dto;

import com.techpulse.taskmanagement.entity.TaskStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(
        @NotNull(message = "status is required (TODO, IN_PROGRESS or DONE)")
        TaskStatus status) {
}
