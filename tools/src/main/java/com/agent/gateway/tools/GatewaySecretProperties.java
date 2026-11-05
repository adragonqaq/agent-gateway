package com.agent.gateway.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关密钥相关配置。
 * <p>所属模块：tools。绑定 {@code gateway.secret}，为 Header 加解密提供主密钥。
 */
@ConfigurationProperties(prefix = "gateway.secret")
public class GatewaySecretProperties {

    /** AES 主密钥明文（至少 16 字节，实际使用前 16 字节）。 */
    private String masterKey = "0123456789abcdef";

    /**
     * 获取主密钥字符串。
     *
     * @return 主密钥
     */
    public String getMasterKey() {
        return masterKey;
    }

    /**
     * 设置主密钥字符串。
     *
     * @param masterKey 主密钥
     */
    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    /**
     * 将主密钥转为 UTF-8 字节，供 {@link SecretBox} 使用。
     *
     * @return 主密钥字节数组
     */
    public byte[] masterKeyBytes() {
        return masterKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
