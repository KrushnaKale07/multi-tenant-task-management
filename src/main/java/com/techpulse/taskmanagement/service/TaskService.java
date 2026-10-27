package com.techpulse.taskmanagement.service;

import com.techpulse.taskmanagement.dto.AssignTaskRequest;
import com.techpulse.taskmanagement.dto.CreateTaskRequest;
import com.techpulse.taskmanagement.dto.TaskResponse;
import com.techpulse.taskmanagement.dto.UpdateTaskStatusRequest;
import com.techpulse.taskmanagement.entity.Role;
import com.techpulse.taskmanagement.entity.Task;
import com.techpulse.taskmanagement.entity.TaskStatus;
import com.techpulse.taskmanagement.exception.ResourceNotFoundException;
import com.techpulse.taskmanagement.repository.TaskRepository;
import com.techpulse.taskmanagement.repository.UserRepository;
import com.techpulse.taskmanagement.security.AuthenticatedUser;
import com.techpulse.taskmanagement.security.SecurityUtils;
import com.techpulse.taskmanagement.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        Task task = Task.builder()
                .title(request.title().trim())
                .description(request.description())
                .status(TaskStatus.TODO)
                .tenantId(tenantId)
                .build();
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse assign(Long taskId, AssignTaskRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        Task task = findTask(taskId, tenantId);
        // The assignee must exist in the SAME tenant, otherwise it is "not found".
        userRepository.findByIdAndTenantId(request.userId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userId()));
        task.setAssignedTo(request.userId());
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateStatus(Long taskId, UpdateTaskStatusRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        Task task = findTask(taskId, tenantId);
        if (!currentUser.userId().equals(task.getAssignedTo())) {
            throw new AccessDeniedException("You can update only tasks assigned to you");
        }
        task.setStatus(request.status());
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list() {
        Long tenantId = TenantContext.requireTenantId();
        AuthenticatedUser currentUser = SecurityUtils.currentUser();
        List<Task> tasks = currentUser.role() == Role.USER
                ? taskRepository.findAllByTenantIdAndAssignedToOrderByIdAsc(tenantId, currentUser.userId())
                : taskRepository.findAllByTenantIdOrderByIdAsc(tenantId);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    private Task findTask(Long taskId, Long tenantId) {
        return taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }
}
