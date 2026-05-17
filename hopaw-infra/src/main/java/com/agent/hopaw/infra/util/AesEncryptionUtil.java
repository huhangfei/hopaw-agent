package com.agent.hopaw.infra.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesEncryptionUtil {

    private static final Logger log = LoggerFactory.getLogger(AesEncryptionUtil.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ENCRYPTED_PREFIX = "{AES}";

    private static SecretKey secretKey;

    private final Path keyPath;

    public AesEncryptionUtil() {
        String home = System.getProperty("user.home");
        this.keyPath = Paths.get(home, ".hopaw", "encryption.key");
    }

    @PostConstruct
    public void init() {
        try {
            File keyFile = keyPath.toFile();
            if (keyFile.exists()) {
                secretKey = loadKey(keyFile);
                log.info("已从 {} 加载加密密钥", keyPath);
            } else {
                secretKey = generateKey();
                saveKey(keyFile);
                log.info("已生成并保存加密密钥到 {}", keyPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("初始化加密密钥失败", e);
        }
    }

    private SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE, new SecureRandom());
        return keyGen.generateKey();
    }

    private void saveKey(File keyFile) throws IOException {
        File parent = keyFile.getParentFile();
        if (!parent.exists()) {
            Files.createDirectories(parent.toPath());
        }
        byte[] keyBytes = secretKey.getEncoded();
        try (FileOutputStream fos = new FileOutputStream(keyFile)) {
            fos.write(keyBytes);
        }
        // 设置文件权限为仅owner可读写 (仅POSIX系统有效)
        keyFile.setReadable(false, false);
        keyFile.setReadable(true, true);
        keyFile.setWritable(false, false);
        keyFile.setWritable(true, true);
    }

    private SecretKey loadKey(File keyFile) throws IOException {
        byte[] keyBytes;
        try (FileInputStream fis = new FileInputStream(keyFile)) {
            keyBytes = fis.readAllBytes();
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 加密明文，返回带前缀的 Base64 密文
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        if (plainText.startsWith(ENCRYPTED_PREFIX)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] combined = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, combined, GCM_IV_LENGTH, cipherText.length);

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密密文，如果未加密则原文返回
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return cipherText;
        }
        if (!cipherText.startsWith(ENCRYPTED_PREFIX)) {
            return cipherText;
        }
        try {
            String base64 = cipherText.substring(ENCRYPTED_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainText = cipher.doFinal(encrypted);
            return new String(plainText, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
