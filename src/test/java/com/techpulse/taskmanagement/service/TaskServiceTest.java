package com.techpulse.taskmanagement.service;

import com.techpulse.taskmanagement.dto.AssignTaskRequest;
import com.techpulse.taskmanagement.dto.CreateTaskRequest;
import com.techpulse.taskmanagement.dto.TaskResponse;
import com.techpulse.taskmanagement.dto.UpdateTaskStatusRequest;
import com.techpulse.taskmanagement.entity.Role;
import com.techpulse.taskmanagement.entity.Task;
import com.techpulse.taskmanagement.entity.TaskStatus;
import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.exception.ResourceNotFoundException;
import com.techpulse.taskmanagement.repository.TaskRepository;
import com.techpulse.taskmanagement.repository.UserRepository;
import com.techpulse.taskmanagement.security.AuthenticatedUser;
import com.techpulse.taskmanagement.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Long TENANT = 1L;
    private static final Long CURRENT_USER = 10L;

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        authenticateAs(Role.USER);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(CURRENT_USER, "u@acme.com", TENANT, role);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private Task task(Long assignedTo) {
        return Task.builder().id(100L).title("t").status(TaskStatus.TODO).assignedTo(assignedTo).tenantId(TENANT).build();
    }

    @Test
    void createUsesTenantFromContext() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(new CreateTaskRequest("  Write docs ", "desc"));

        assertThat(response.tenantId()).isEqualTo(TENANT);
        assertThat(response.title()).isEqualTo("Write docs");
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.assignedTo()).isNull();
    }

    @Test
    void createFailsWithoutTenantContext() {
        TenantContext.clear();
        CreateTaskRequest request = new CreateTaskRequest("x", null);
        assertThatThrownBy(() -> taskService.create(request))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void assignSetsAssigneeOfSameTenant() {
        when(taskRepository.findByIdAndTenantId(100L, TENANT)).thenReturn(Optional.of(task(null)));
        when(userRepository.findByIdAndTenantId(20L, TENANT))
                .thenReturn(Optional.of(User.builder().id(20L).tenantId(TENANT).role(Role.USER).build()));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.assign(100L, new AssignTaskRequest(20L));

        assertThat(response.assignedTo()).isEqualTo(20L);
    }

    @Test
    void assignFailsWhenTaskNotInTenant() {
        when(taskRepository.findByIdAndTenantId(100L, TENANT)).thenReturn(Optional.empty());
        AssignTaskRequest request = new AssignTaskRequest(20L);

        assertThatThrownBy(() -> taskService.assign(100L, request)).isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void assignFailsWhenUserNotInTenant() {
        when(taskRepository.findByIdAndTenantId(100L, TENANT)).thenReturn(Optional.of(task(null)));
        when(userRepository.findByIdAndTenantId(20L, TENANT)).thenReturn(Optional.empty());
        AssignTaskRequest request = new AssignTaskRequest(20L);

        assertThatThrownBy(() -> taskService.assign(100L, request)).isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateStatusAllowedForAssignee() {
        when(taskRepository.findByIdAndTenantId(100L, TENANT)).thenReturn(Optional.of(task(CURRENT_USER)));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.updateStatus(100L, new UpdateTaskStatusRequest(TaskStatus.DONE));

        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void updateStatusDeniedForNonAssignee() {
        when(taskRepository.findByIdAndTenantId(100L, TENANT)).thenReturn(Optional.of(task(99L)));
        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest(TaskStatus.DONE);

        assertThatThrownBy(() -> taskService.updateStatus(100L, request)).isInstanceOf(AccessDeniedException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateStatusFailsWhenTaskNotInTenant() {
        when(taskRepository.findByIdAndTenantId(100L, TENANT)).thenReturn(Optional.empty());
        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest(TaskStatus.DONE);

        assertThatThrownBy(() -> taskService.updateStatus(100L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void userListsOnlyAssignedTasks() {
        when(taskRepository.findAllByTenantIdAndAssignedToOrderByIdAsc(TENANT, CURRENT_USER))
                .thenReturn(List.of(task(CURRENT_USER)));

        assertThat(taskService.list()).hasSize(1);
        verify(taskRepository, never()).findAllByTenantIdOrderByIdAsc(any());
    }

    @Test
    void managerAndAdminListAllTasksOfTheirTenant() {
        when(taskRepository.findAllByTenantIdOrderByIdAsc(TENANT)).thenReturn(List.of(task(null), task(5L)));

        authenticateAs(Role.MANAGER);
        assertThat(taskService.list()).hasSize(2);

        authenticateAs(Role.ADMIN);
        assertThat(taskService.list()).hasSize(2);
        verify(taskRepository, never()).findAllByTenantIdAndAssignedToOrderByIdAsc(any(), any());
    }
}
