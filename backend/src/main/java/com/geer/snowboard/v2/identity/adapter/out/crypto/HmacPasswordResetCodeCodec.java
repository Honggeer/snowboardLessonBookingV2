package com.geer.snowboard.v2.identity.adapter.out.crypto;

import com.geer.snowboard.v2.identity.application.port.out.PasswordResetCodeCodec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HmacPasswordResetCodeCodec implements PasswordResetCodeCodec {
    private static final BigInteger CODE_SPACE = BigInteger.valueOf(100_000_000L);
    private final byte[] key;

    public HmacPasswordResetCodeCodec(@Value("${identity.verification-key:}") String secret) {
        key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) throw new IllegalStateException("VERIFICATION_KEY must be at least 32 UTF-8 bytes");
    }

    @Override public String issue(String resetId) {
        long value = new BigInteger(1, mac("password-reset-code:v1:" + resetId)).mod(CODE_SPACE).longValue();
        return String.format(Locale.ROOT, "%08d", value);
    }

    @Override public byte[] digest(String resetId, String code) {
        return mac("password-reset-digest:v1:" + resetId + ":" + code);
    }

    private byte[] mac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
