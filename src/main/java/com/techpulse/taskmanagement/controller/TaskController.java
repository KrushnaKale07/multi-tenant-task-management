package com.techpulse.taskmanagement.controller;

import com.techpulse.taskmanagement.dto.AssignTaskRequest;
import com.techpulse.taskmanagement.dto.CreateTaskRequest;
import com.techpulse.taskmanagement.dto.TaskResponse;
import com.techpulse.taskmanagement.dto.UpdateTaskStatusRequest;
import com.techpulse.taskmanagement.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(request);
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('MANAGER')")
    public TaskResponse assign(@PathVariable Long id, @Valid @RequestBody AssignTaskRequest request) {
        return taskService.assign(id, request);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('USER')")
    public TaskResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateTaskStatusRequest request) {
        return taskService.updateStatus(id, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public List<TaskResponse> list() {
        return taskService.list();
    }
}
