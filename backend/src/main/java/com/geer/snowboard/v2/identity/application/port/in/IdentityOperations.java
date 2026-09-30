package com.geer.snowboard.v2.identity.application.port.in;

public interface IdentityOperations {
    void register(RegisterCommand command, String sourceIp);
    boolean verify(String token);
    void resend(String email, String sourceIp);
    AccountView authenticate(String email, String password, String sourceIp);
    AccountView findById(String id);
    void createCoach(String name, String email, String password);
}
