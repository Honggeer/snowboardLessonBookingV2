package com.geer.snowboard.v2.identity.adapter.out.crypto;

import com.geer.snowboard.v2.identity.application.port.out.VerificationTokenCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HmacVerificationTokenCodec implements VerificationTokenCodec {
    private final byte[] key;
    public HmacVerificationTokenCodec(@Value("${identity.verification-key:}") String secret) {
        key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalStateException("VERIFICATION_KEY must be at least 32 UTF-8 bytes");
    }
    @Override public String issue(String id) {
        return id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(id));
    }
    @Override public String idOf(String token) {
        if (token == null || token.length() > 128) return null;
        int separator = token.indexOf('.');
        if (separator < 0) return null;
        String id = token.substring(0, separator);
        try {
            UUID.fromString(id);
            byte[] supplied = Base64.getUrlDecoder().decode(token.substring(separator + 1));
            return MessageDigest.isEqual(mac(id), supplied) ? id : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
    @Override public byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private byte[] mac(String id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(id.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
