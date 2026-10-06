package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.*;
import com.geer.snowboard.v2.identity.domain.ContactPhone;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ContactPhoneTest {
    @Test void normalizesFormattingWhilePreservingExplicitCountryCodes() {
        assertThat(ContactPhone.parse(" +1 (416) 555-0123 ").value()).isEqualTo("+14165550123");
        assertThat(ContactPhone.parse("+86 138 0013 8000").value()).isEqualTo("+8613800138000");
    }
    @Test void rejectsMissingCountryCodeIllegalCharactersAndInvalidLengths() {
        for (String input : Arrays.asList(null, "", "  ", "4165550123", "+01234567", "+123456",
                "+1234567890123456", "+1/4165550123", "+1abc5550123", "+１４１６５５５０１２３", " ".repeat(49) + "+123456789012345"))
            assertThatIllegalArgumentException().as("invalid phone: %s", input).isThrownBy(() -> ContactPhone.parse(input));
    }
    @Test void permitsDigitAndRawInputBoundaries() {
        assertThat(ContactPhone.parse("+1234567").value()).isEqualTo("+1234567");
        assertThat(ContactPhone.parse(" ".repeat(48) + "+123456789012345").value()).isEqualTo("+123456789012345");
    }
    @Test void directValueConstructionAlsoEnforcesCanonicalPhone() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ContactPhone("not-a-phone"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ContactPhone(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ContactPhone("+1 416 555 0123"));
    }
}
