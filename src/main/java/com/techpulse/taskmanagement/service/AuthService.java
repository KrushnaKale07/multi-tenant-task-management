package com.techpulse.taskmanagement.service;

import com.techpulse.taskmanagement.dto.LoginRequest;
import com.techpulse.taskmanagement.dto.LoginResponse;
import com.techpulse.taskmanagement.entity.User;
import com.techpulse.taskmanagement.repository.UserRepository;
import com.techpulse.taskmanagement.security.JwtService;
import com.techpulse.taskmanagement.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BEARER = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Same error for unknown e-mail and wrong password: no user enumeration.
        User user = userRepository.findByEmail(EmailNormalizer.normalize(request.email()))
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        return new LoginResponse(jwtService.generateToken(user), BEARER, jwtService.getExpirationSeconds(),
                user.getTenantId(), user.getRole());
    }
}
