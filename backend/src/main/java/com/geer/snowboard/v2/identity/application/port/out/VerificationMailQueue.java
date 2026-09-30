package com.geer.snowboard.v2.identity.application.port.out;

import java.time.Instant;

public interface VerificationMailQueue {
    record Task(long id, String verificationId, String email, int attempts) {}
    Task claim(Instant now);
    boolean isValid(long taskId, Instant now);
    void sent(long taskId);
    void failed(long taskId, Instant nextAttempt, String reason);
    void skipped(long taskId);
    void cleanup(Instant now);
}
