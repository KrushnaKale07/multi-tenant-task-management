package com.techpulse.taskmanagement.repository;

import org.springframework.data.repository.Repository;

import com.techpulse.taskmanagement.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends Repository<User, Long> {

    User save(User user);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndTenantId(Long id, Long tenantId);

    List<User> findAllByTenantIdOrderByIdAsc(Long tenantId);
}
