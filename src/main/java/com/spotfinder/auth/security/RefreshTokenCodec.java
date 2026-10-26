package com.spotfinder.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCodec {

    private final SecureRandom random = new SecureRandom();
    private final byte[] signingKey;

    public RefreshTokenCodec(@Value("${app.jwt.secret}") String secret) {
        signingKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isValidFormat(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    public String hash(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot hash refresh token", exception);
        }
    }

    public String successor(String token, UUID requestId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));

            String input = "spotfinder-refresh-v1\n" + token + "\n" + requestId;

            byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot rotate refresh token", exception);
        }
    }
}
