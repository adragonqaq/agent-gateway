package com.agent.gateway.tools;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加解密工具盒。
 * <p>所属模块：tools。用于 MCP 请求头等敏感字段落库前加密、读取时解密。
 */
public final class SecretBox {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    /**
     * 使用主密钥构造加密盒；不足 16 字节拒绝，超过则截取前 16 字节。
     *
     * @param masterKey 主密钥字节
     * @throws IllegalArgumentException 密钥为空或短于 16 字节
     */
    public SecretBox(byte[] masterKey) {
        if (masterKey == null || masterKey.length < 16) {
            throw new IllegalArgumentException("master key must be at least 16 bytes");
        }
        // AES-128 固定 16 字节；更长主密钥只取前缀，避免算法与密钥长度不匹配
        byte[] normalized = new byte[16];
        System.arraycopy(masterKey, 0, normalized, 0, Math.min(masterKey.length, 16));
        this.key = new SecretKeySpec(normalized, "AES");
    }

    /**
     * 加密明文；输出为 Base64(IV || ciphertext+tag)。
     *
     * @param plaintext 明文
     * @return Base64 密文
     * @throws IllegalStateException 加密失败
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
            buf.put(iv);
            buf.put(ciphertext);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    /**
     * 解密 {@link #encrypt(String)} 产出的密文。
     *
     * @param blob Base64 密文
     * @return 明文
     * @throws IllegalStateException 解密失败
     */
    public String decrypt(String blob) {
        try {
            byte[] all = Base64.getDecoder().decode(blob);
            byte[] iv = new byte[IV_LEN];
            byte[] ciphertext = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }
}
