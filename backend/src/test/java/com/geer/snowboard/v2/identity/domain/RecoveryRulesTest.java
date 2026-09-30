package com.geer.snowboard.v2.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RecoveryRulesTest {
    @Test void passwordUsesUnicodeCodePointsAndRequiresExactConfirmation() {
        assertThatThrownBy(() -> RecoveryRules.password("雪".repeat(7), "雪".repeat(7)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(RecoveryRules.password("雪".repeat(8), "雪".repeat(8))).isEqualTo("雪".repeat(8));
        assertThat(RecoveryRules.password("😀".repeat(128), "😀".repeat(128))).isEqualTo("😀".repeat(128));
        assertThatThrownBy(() -> RecoveryRules.password("😀".repeat(129), "😀".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecoveryRules.password("password1", "password2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void codeKeepsLeadingZeroesAndEmailUsesExistingCaseInsensitiveKey() {
        assertThat(RecoveryRules.code("01234567")).isEqualTo("01234567");
        assertThatThrownBy(() -> RecoveryRules.code("1234567"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(RecoveryRules.emailKey("  Rider@Example.Test  ")).isEqualTo("rider@example.test");
    }
}
