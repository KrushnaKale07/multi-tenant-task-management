package com.techpulse.taskmanagement.controller;

import com.techpulse.taskmanagement.dto.RegisterTenantRequest;
import com.techpulse.taskmanagement.dto.TenantRegistrationResponse;
import com.techpulse.taskmanagement.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantRegistrationResponse register(@Valid @RequestBody RegisterTenantRequest request) {
        return tenantService.register(request);
    }
}
