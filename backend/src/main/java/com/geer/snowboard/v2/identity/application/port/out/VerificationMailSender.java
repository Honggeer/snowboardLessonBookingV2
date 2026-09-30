package com.geer.snowboard.v2.identity.application.port.out;

public interface VerificationMailSender {
    void send(String email, String link);
}
