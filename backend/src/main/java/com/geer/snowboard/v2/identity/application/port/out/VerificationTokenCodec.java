package com.geer.snowboard.v2.identity.application.port.out;

public interface VerificationTokenCodec {
    String issue(String id);
    String idOf(String token);
    byte[] digest(String token);
}
