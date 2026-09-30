package com.geer.snowboard.v2.identity.application.port.out;

public interface PasswordResetCodeCodec {
    String issue(String resetId);
    byte[] digest(String resetId, String code);
}
