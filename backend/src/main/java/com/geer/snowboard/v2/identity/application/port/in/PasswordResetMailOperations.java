package com.geer.snowboard.v2.identity.application.port.in;

public interface PasswordResetMailOperations {
    void runOnce();
    void cleanup();
}
