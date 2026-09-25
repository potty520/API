package com.example.ingestion.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

@Service
public class CryptoService {
    private final SecureRandom random = new SecureRandom();
    private final Path secretFile;
    private byte[] key;
    private SecretKeySpec auditKey;

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
        // 审计链签名使用从主密钥派生的独立子密钥, 与配置加密密钥隔离
        byte[] derived = java.security.MessageDigest.getInstance("SHA-256")
                .digest((Base64.getEncoder().encodeToString(key) + "|audit-chain-v2").getBytes(StandardCharsets.UTF_8));
        auditKey = new SecretKeySpec(derived, "HmacSHA256");
        restrictSecretFile();
    }

    /** 主密钥文件收紧为仅属主可读写; 非 POSIX 文件系统(Windows/NTFS)按部署文档的 ACL 步骤处理。 */
    private void restrictSecretFile() {
        try {
            if (!secretFile.getFileSystem().supportedFileAttributeViews().contains("posix")) return;
            Files.setPosixFilePermissions(secretFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (Exception ignored) {
            // 权限设置失败不影响启动, 由部署清单里的 ACL 检查兜底
        }
    }

    /** 审计哈希链专用: 没有主密钥就无法重算出合法的 row_hash。 */
    public String hmacSha256Hex(String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(auditKey);
            return HexFormat.of().formatHex(mac.doFinal((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("审计链签名失败", error);
        }
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
