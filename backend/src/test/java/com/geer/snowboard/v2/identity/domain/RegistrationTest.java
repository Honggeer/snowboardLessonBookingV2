package com.geer.snowboard.v2.identity.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RegistrationTest {
    @Test
    void acceptsOnlyTheThreeApprovedLevelsAndAllRequiredFields() {
        for (String level : new String[]{"零基础", "入门", "进阶"}) {
            Registration registration = Registration.create(" Geer ", level, " Geer@Example.COM ", "很长的 password 123");
            assertEquals("Geer", registration.name());
            assertEquals("Geer@Example.COM", registration.email());
            assertEquals("geer@example.com", registration.emailKey());
            assertEquals(level, registration.level().label());
        }
        assertThrows(IllegalArgumentException.class, () -> Registration.create("Geer", "高手", "a@example.com", "很长的 password 123"));
        assertThrows(IllegalArgumentException.class, () -> Registration.create(" ", "入门", "a@example.com", "很长的 password 123"));
        assertThrows(IllegalArgumentException.class, () -> Registration.create("Geer", "入门", "bad-email", "很长的 password 123"));
        assertThrows(IllegalArgumentException.class, () -> Registration.create("Geer", "入门", "a@example.com", "short"));
    }

    @Test
    void acceptsUnicodeAndSpacesUpTo128CharactersWithoutTruncation() {
        String password = "雪".repeat(128);
        assertEquals(password, Registration.create("Geer", "进阶", "a@example.com", password).password());
        assertThrows(IllegalArgumentException.class, () -> Registration.create("Geer", "进阶", "a@example.com", "雪".repeat(129)));
    }

    @Test
    void acceptsEightUnicodeCodePointsAndRejectsSevenForStudentsAndCoachInput() {
        String eight = "雪".repeat(8);
        assertEquals(eight, Registration.create("Student", "入门", "student@example.com", eight).password());
        assertEquals(eight, Registration.create("GEER", "零基础", "coach@example.com", eight).password());
        assertThrows(IllegalArgumentException.class,
                () -> Registration.create("Student", "入门", "student@example.com", "雪".repeat(7)));
        assertEquals("😀".repeat(8), Registration.create("Student", "进阶", "emoji@example.com", "😀".repeat(8)).password());
        assertThrows(IllegalArgumentException.class,
                () -> Registration.create("Student", "进阶", "emoji@example.com", "😀".repeat(7)));
    }
}
