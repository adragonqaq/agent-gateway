package com.agent.gateway.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.secret")
public class GatewaySecretProperties {

    private String masterKey = "0123456789abcdef";

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public byte[] masterKeyBytes() {
        return masterKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
