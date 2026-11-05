package com.agent.gateway.tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * API Key 哈希工具。
 * 属于 tenant 模块；将明文 Key 转为 SHA-256 十六进制摘要后入库/查询，避免明文落库与日志泄露。
 */
public final class ApiKeyHasher {

    /**
     * 工具类不可实例化。
     */
    private ApiKeyHasher() {
    }

    /**
     * 对原始 API Key 做 SHA-256 摘要并输出小写十六进制。
     *
     * @param rawKey 明文 API Key
     * @return 64 位十六进制哈希
     * @throws IllegalStateException 运行时缺少 SHA-256 算法实现
     */
    public static String sha256Hex(String rawKey) {
        try {
            // 使用 SHA-256：不可逆摘要，便于按哈希比对且不存明文
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
