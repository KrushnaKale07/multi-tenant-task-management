package com.techpulse.taskmanagement.repository;

import org.springframework.data.repository.Repository;

import com.techpulse.taskmanagement.entity.Tenant;

public interface TenantRepository extends Repository<Tenant, Long> {

    Tenant save(Tenant tenant);

    boolean existsByNameIgnoreCase(String name);
}
