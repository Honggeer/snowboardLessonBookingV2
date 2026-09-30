package com.geer.snowboard.v2.identity.application.port.out;

import java.time.Instant;

public interface IdentityStore {
    record Account(String id, String name, String level, String email, String emailKey,
                   String role, String passwordHash, Instant verifiedAt) {}
    boolean insertAccount(Account account, Instant now);
    Account findByEmail(String emailKey);
    Account findById(String id);
    Account lockById(String id);
    boolean coachExists();
    void replaceVerification(String id, String accountId, byte[] digest, Instant expiresAt, Instant now);
    String accountIdForVerification(String id);
    boolean consumeVerification(String id, byte[] digest, Instant now);
    boolean mayResend(String accountId, Instant now);
    boolean allowAttempt(String key, int max, Instant now, long windowSeconds);
    int attempts(String key, Instant now);
}
