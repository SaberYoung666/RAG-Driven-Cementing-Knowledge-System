package org.swpu.backend.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String tokenSecret;
    private final long expireSeconds;

    public TokenService(
            @Value("${auth.token-secret:change-me-please-use-env}") String tokenSecret,
            @Value("${auth.token-expire-seconds:86400}") long expireSeconds) {
        this.tokenSecret = tokenSecret;
        this.expireSeconds = expireSeconds;
    }

    // 根据用户id和用户名生成token
    public String generateToken(Long userId, String username) {
        long expiresAt = Instant.now().getEpochSecond() + expireSeconds;
        String payload = userId + ":" + username + ":" + expiresAt;
        String payloadEncoded = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        String signature = base64Url(hmacSha256(payload));
        return payloadEncoded + "." + signature;
    }

    public TokenUser verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return null;
        }

        byte[] payloadBytes;
        byte[] signatureBytes;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            signatureBytes = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        byte[] expected = hmacSha256(payload);
        if (!MessageDigest.isEqual(expected, signatureBytes)) {
            return null;
        }

        String[] payloadParts = payload.split(":", 3);
        if (payloadParts.length != 3) {
            return null;
        }

        Long userId;
        long expiresAt;
        try {
            userId = Long.parseLong(payloadParts[0]);
            expiresAt = Long.parseLong(payloadParts[2]);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            return null;
        }

        return new TokenUser(userId, payloadParts[1]);
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    private byte[] hmacSha256(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("token sign failed", ex);
        }
    }

    private String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
