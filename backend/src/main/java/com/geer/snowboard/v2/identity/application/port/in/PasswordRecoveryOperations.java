package com.geer.snowboard.v2.identity.application.port.in;

import java.io.Serializable;
import java.time.Instant;

public interface PasswordRecoveryOperations {
    record Grant(String accountId, String resetId, Instant expiresAt) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    void request(String email, String sourceIp);
    Grant verify(String email, String code, String sourceIp);
    boolean complete(Grant grant, String newPassword, String confirmPassword);
}
