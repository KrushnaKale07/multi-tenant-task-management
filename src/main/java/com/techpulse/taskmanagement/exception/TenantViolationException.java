package com.techpulse.taskmanagement.exception;

public class TenantViolationException extends RuntimeException {

    public TenantViolationException(String message) {
        super(message);
    }
}
