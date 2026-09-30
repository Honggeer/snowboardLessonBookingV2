package com.geer.snowboard.v2.identity.application.port.out;

public interface PasswordResetMailSender {
    void send(String email, String code);
}
