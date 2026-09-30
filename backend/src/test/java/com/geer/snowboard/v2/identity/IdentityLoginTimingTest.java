package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geer.snowboard.v2.identity.application.port.out.IdentityStore;
import com.geer.snowboard.v2.identity.application.port.out.PasswordHashes;
import com.geer.snowboard.v2.identity.application.port.out.VerificationTokenCodec;
import com.geer.snowboard.v2.identity.application.service.IdentityService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IdentityLoginTimingTest {
    @Test void unknownEmailStillRunsPasswordVerification() {
        IdentityStore store = Mockito.mock(IdentityStore.class);
        PasswordHashes hashes = Mockito.mock(PasswordHashes.class);
        VerificationTokenCodec tokens = Mockito.mock(VerificationTokenCodec.class);
        when(hashes.encode(anyString())).thenReturn("dummy-hash");
        IdentityService service = new IdentityService(store, hashes, tokens, Clock.systemUTC());
        assertThat(service.authenticate("missing@example.com", "wrong password", "127.0.0.1")).isNull();
        verify(hashes).matches(eq("wrong password"), eq("dummy-hash"));
    }
}
