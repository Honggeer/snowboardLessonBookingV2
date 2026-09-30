package com.geer.snowboard.v2.identity.adapter.out.crypto;

import com.geer.snowboard.v2.identity.application.port.out.PasswordHashes;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class Pbkdf2PasswordHashes implements PasswordHashes {
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    @Override public String encode(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] result = derive(password, salt);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(result);
    }
    @Override public boolean matches(String password, String encoded) {
        if (password == null || encoded == null) return false;
        String[] parts = encoded.split("\\$");
        if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0]) || !Integer.toString(ITERATIONS).equals(parts[1])) return false;
        try {
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, Base64.getDecoder().decode(parts[2]));
            boolean match = MessageDigest.isEqual(expected, actual);
            Arrays.fill(actual, (byte) 0);
            return match;
        } catch (IllegalArgumentException exception) { return false; }
    }
    private byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
        finally { spec.clearPassword(); }
    }
}
