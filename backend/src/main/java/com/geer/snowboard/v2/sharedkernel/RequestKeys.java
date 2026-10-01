package com.geer.snowboard.v2.sharedkernel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class RequestKeys {
    private RequestKeys() {}

    public static String key(String value) {
        try { return UUID.fromString(value).toString(); }
        catch (RuntimeException error) { throw new BusinessProblem(400, "请提供有效的 Idempotency-Key"); }
    }

    public static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    public static int limit(Integer requested) {
        if (requested == null) return 20;
        if (requested < 1 || requested > 50) throw new BusinessProblem(400, "每页数量须在 1–50 之间");
        return requested;
    }
}
