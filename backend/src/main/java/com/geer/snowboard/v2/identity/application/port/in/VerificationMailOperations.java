package com.geer.snowboard.v2.identity.application.port.in;

/** Scheduled entry points for the verification mail use case. */
public interface VerificationMailOperations {
    void runOnce();
    void cleanup();
}
