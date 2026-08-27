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

    /** 实例密钥：通过 new AesEncryptionUtil(keyBytes) 指定（如备份导入时使用压缩包内旧密钥） */
    private final SecretKey instanceKey;

    private final Path keyPath;

    public AesEncryptionUtil() {
        String home = System.getProperty("user.home");
        this.keyPath = Paths.get(home, ".hopaw", "encryption.key");
        this.instanceKey = null;
    }

    /**
     * 使用外部指定的密钥构造实例（不触发 @PostConstruct，不影响本机静态密钥）。
     * 用于备份导入等场景：用备份包内的旧密钥解密历史密文。
     */
    public AesEncryptionUtil(byte[] keyBytes) {
        String home = System.getProperty("user.home");
        this.keyPath = Paths.get(home, ".hopaw", "encryption.key");
        this.instanceKey = new SecretKeySpec(keyBytes, ALGORITHM);
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

    /**
     * 重新加载磁盘上的密钥（用于导入备份后使新密钥立即生效）
     */
    public void reload() {
        try {
            File keyFile = keyPath.toFile();
            if (!keyFile.exists()) {
                throw new IllegalStateException("密钥文件不存在: " + keyPath);
            }
            secretKey = loadKey(keyFile);
            log.info("已重新加载加密密钥: {}", keyPath);
        } catch (Exception e) {
            throw new RuntimeException("重新加载密钥失败", e);
        }
    }

    /**
     * 返回当前密钥文件路径
     */
    public String getKeyPath() {
        return keyPath.toString();
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
     * 判断值是否为 {AES} 密文格式
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    /**
     * 加密明文，返回带前缀的 Base64 密文（使用本机密钥）
     */
    public static String encrypt(String plainText) {
        return doEncrypt(plainText, secretKey);
    }

    /**
     * 解密密文，如果未加密则原文返回（使用本机密钥）
     */
    public static String decrypt(String cipherText) {
        return doDecrypt(cipherText, secretKey);
    }

    /**
     * 使用实例密钥加密（配合 new AesEncryptionUtil(byte[] keyBytes) 使用）
     */
    public String encryptWith(String plainText) {
        requireInstanceKey();
        return doEncrypt(plainText, instanceKey);
    }

    /**
     * 使用实例密钥解密（配合 new AesEncryptionUtil(byte[] keyBytes) 使用）
     */
    public String decryptWith(String cipherText) {
        requireInstanceKey();
        return doDecrypt(cipherText, instanceKey);
    }

    private void requireInstanceKey() {
        if (instanceKey == null) {
            throw new IllegalStateException("未指定实例密钥，请使用 AesEncryptionUtil(byte[] keyBytes) 构造");
        }
    }

    private static String doEncrypt(String plainText, SecretKey key) {
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
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] combined = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, combined, GCM_IV_LENGTH, cipherText.length);

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    private static String doDecrypt(String cipherText, SecretKey key) {
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
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plainText = cipher.doFinal(encrypted);
            return new String(plainText, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
