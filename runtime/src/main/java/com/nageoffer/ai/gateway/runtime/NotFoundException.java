package com.nageoffer.ai.gateway.runtime;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
