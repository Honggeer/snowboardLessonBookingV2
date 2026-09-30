package com.geer.snowboard.v2.identity.application.port.out;

import java.time.Instant;

public interface PasswordResetStore {
    record Reset(String id, String accountId, byte[] codeDigest, Instant expiresAt, int wrongAttempts,
                 Instant consumedAt, Instant invalidatedAt, Instant createdAt) {}
    Reset latest(String accountId);
    void create(String id, String accountId, byte[] digest, Instant expiresAt, Instant now);
    void wrongAttempt(String id, Instant now);
    boolean consume(String id, Instant now);
}
