package com.geer.snowboard.v2.identity.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Registration {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final String name;
    private final Level level;
    private final String email;
    private final String emailKey;
    private final String password;

    private Registration(String name, Level level, String email, String emailKey, String password) {
        this.name = name;
        this.level = level;
        this.email = email;
        this.emailKey = emailKey;
        this.password = password;
    }

    public static Registration create(String name, String level, String email, String password) {
        if (name == null || name.isBlank() || name.strip().codePointCount(0, name.strip().length()) > 100) {
            throw new IllegalArgumentException("Invalid name");
        }
        Level parsedLevel = Level.fromLabel(level);
        if (email == null || !EMAIL.matcher(email.strip()).matches() || email.strip().length() > 254) {
            throw new IllegalArgumentException("Invalid email");
        }
        if (password == null) throw new IllegalArgumentException("Invalid password");
        int length = password.codePointCount(0, password.length());
        if (length < 8 || length > 128) throw new IllegalArgumentException("Invalid password");
        String displayEmail = email.strip();
        return new Registration(name.strip(), parsedLevel, displayEmail,
                displayEmail.toLowerCase(Locale.ROOT), password);
    }

    public String name() { return name; }
    public Level level() { return level; }
    public String email() { return email; }
    public String emailKey() { return emailKey; }
    public String password() { return password; }

    @Override
    public String toString() { return "Registration[redacted]"; }
}
