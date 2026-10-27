package com.techpulse.taskmanagement.service;

import com.techpulse.taskmanagement.dto.RegisterTenantRequest;
import com.techpulse.taskmanagement.dto.TenantRegistrationResponse;
import com.techpulse.taskmanagement.entity.Role;
import com.techpulse.taskmanagement.entity.Tenant;
import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.exception.DuplicateResourceException;
import com.techpulse.taskmanagement.repository.TenantRepository;
import com.techpulse.taskmanagement.repository.UserRepository;
import com.techpulse.taskmanagement.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Creates the tenant and its first ADMIN user atomically. */
    @Transactional
    public TenantRegistrationResponse register(RegisterTenantRequest request) {
        String tenantName = request.tenantName().trim();
        String email = EmailNormalizer.normalize(request.email());

        if (tenantRepository.existsByNameIgnoreCase(tenantName)) {
            throw new DuplicateResourceException("A tenant with this name already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A user with this e-mail already exists");
        }

        Tenant tenant = tenantRepository.save(Tenant.builder().name(tenantName).build());
        User admin = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .tenantId(tenant.getId())
                .build());

        return new TenantRegistrationResponse(tenant.getId(), tenant.getName(), admin.getId(), admin.getEmail(),
                tenant.getCreatedAt());
    }
}
