package com.techpulse.taskmanagement.dto;

import java.time.Instant;

import com.techpulse.taskmanagement.entity.Task;
import com.techpulse.taskmanagement.entity.TaskStatus;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Long assignedTo,
        Long tenantId,
        Instant createdAt,
        Instant updatedAt) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getAssignedTo(), task.getTenantId(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
