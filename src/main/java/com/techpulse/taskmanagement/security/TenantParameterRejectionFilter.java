package com.techpulse.taskmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;


public class TenantParameterRejectionFilter extends OncePerRequestFilter {

    private static final Set<String> FORBIDDEN_NAMES = Set.of("tenant_id", "tenantid", "tenant-id", "x-tenant-id");

    private final RestSecurityErrorHandler errorHandler;

    public TenantParameterRejectionFilter(RestSecurityErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (containsTenantParameter(request) || containsTenantHeader(request)) {
            errorHandler.write(request, response, HttpStatus.BAD_REQUEST,
                    "tenant_id must not be supplied by the client; it is resolved from the JWT only");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean containsTenantParameter(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null) {
            return false;
        }
        for (String pair : query.split("&")) {
            String name = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            if (FORBIDDEN_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTenantHeader(HttpServletRequest request) {
        return FORBIDDEN_NAMES.stream().anyMatch(name -> request.getHeader(name) != null);
    }
}
