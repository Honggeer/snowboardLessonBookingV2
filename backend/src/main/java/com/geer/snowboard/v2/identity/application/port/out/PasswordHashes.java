package com.geer.snowboard.v2.identity.application.port.out;

public interface PasswordHashes {
    String encode(String password);
    boolean matches(String password, String encoded);
}
