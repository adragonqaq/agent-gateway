package com.nageoffer.ai.gateway.tenant;

public class MissingTenantException extends RuntimeException {

    public MissingTenantException(String message) {
        super(message);
    }
}
