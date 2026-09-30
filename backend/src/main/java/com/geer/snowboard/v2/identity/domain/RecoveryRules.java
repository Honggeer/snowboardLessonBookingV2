package com.geer.snowboard.v2.identity.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class RecoveryRules {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern CODE = Pattern.compile("[0-9]{8}");

    private RecoveryRules() {}

    public static String emailKey(String email) {
        if (email == null) throw new IllegalArgumentException("Invalid email");
        String normalized = email.strip();
        if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid email");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public static String code(String code) {
        if (code == null || !CODE.matcher(code).matches()) throw new IllegalArgumentException("Invalid code");
        return code;
    }

    public static String password(String password, String confirmation) {
        if (password == null || confirmation == null || !password.equals(confirmation)) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        int length = password.codePointCount(0, password.length());
        if (length < 8 || length > 128) throw new IllegalArgumentException("Invalid password");
        return password;
    }
}
