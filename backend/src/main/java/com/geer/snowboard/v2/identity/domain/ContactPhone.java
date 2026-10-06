package com.geer.snowboard.v2.identity.domain;

/** A canonical contact number; formatting checks do not verify ownership or reachability. */
public record ContactPhone(String value) {
    public ContactPhone {
        if (value == null || !value.matches("\\+[1-9][0-9]{6,14}"))
            throw new IllegalArgumentException("Invalid contact phone");
    }
    public static ContactPhone parse(String input) {
        if (input == null || input.length() > 64) throw new IllegalArgumentException("Invalid contact phone");
        return new ContactPhone(input.replaceAll("[ ()-]", ""));
    }
}
