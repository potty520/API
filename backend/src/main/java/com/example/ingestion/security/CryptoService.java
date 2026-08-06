package com.example.ingestion.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoService {
    private final SecureRandom random = new SecureRandom();
    private final Path secretFile;
    private byte[] key;

    public CryptoService(@Value("${app.secret-file:data/.secret}") String secretFile) {
        this.secretFile = Path.of(secretFile).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() throws Exception {
        Files.createDirectories(secretFile.getParent());
        if (Files.exists(secretFile)) {
            key = Base64.getDecoder().decode(Files.readString(secretFile).trim());
        } else {
            key = new byte[32];
            random.nextBytes(key);
            Files.writeString(secretFile, Base64.getEncoder().encodeToString(key));
        }
        if (key.length != 32) throw new IllegalStateException("加密主密钥必须为 32 字节");
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return "";
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, packed, 0, nonce.length);
            System.arraycopy(encrypted, 0, packed, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception error) {
            throw new IllegalStateException("敏感配置加密失败", error);
        }
    }

    public String decrypt(String packedText) {
        if (packedText == null || packedText.isBlank()) return "";
        try {
            byte[] packed = Base64.getDecoder().decode(packedText);
            byte[] nonce = new byte[12];
            byte[] encrypted = new byte[packed.length - 12];
            System.arraycopy(packed, 0, nonce, 0, 12);
            System.arraycopy(packed, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("敏感配置解密失败，请检查主密钥", error);
        }
    }
}
