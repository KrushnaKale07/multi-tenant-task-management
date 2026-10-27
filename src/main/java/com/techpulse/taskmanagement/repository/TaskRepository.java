package com.techpulse.taskmanagement.repository;

import org.springframework.data.repository.Repository;

import com.techpulse.taskmanagement.entity.Task;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends Repository<Task, Long> {

    Task save(Task task);

    Optional<Task> findByIdAndTenantId(Long id, Long tenantId);

    List<Task> findAllByTenantIdOrderByIdAsc(Long tenantId);

    List<Task> findAllByTenantIdAndAssignedToOrderByIdAsc(Long tenantId, Long assignedTo);
}
