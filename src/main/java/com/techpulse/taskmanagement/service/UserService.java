package com.techpulse.taskmanagement.service;

import com.techpulse.taskmanagement.dto.CreateUserRequest;
import com.techpulse.taskmanagement.dto.UserResponse;
import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.exception.DuplicateResourceException;
import com.techpulse.taskmanagement.repository.UserRepository;
import com.techpulse.taskmanagement.tenant.TenantContext;
import com.techpulse.taskmanagement.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A user with this e-mail already exists");
        }
        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .tenantId(tenantId)
                .build());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAllByTenantIdOrderByIdAsc(TenantContext.requireTenantId())
                .stream().map(UserResponse::from).toList();
    }
}
