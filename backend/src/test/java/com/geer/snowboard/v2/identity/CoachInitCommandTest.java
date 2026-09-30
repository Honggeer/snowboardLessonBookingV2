package com.geer.snowboard.v2.identity;

import static org.mockito.Mockito.verify;

import com.geer.snowboard.v2.identity.adapter.in.cli.CoachInitCommand;
import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CoachInitCommandTest {
    @Test void readsPrivateCoachFieldsFromInput() throws Exception {
        IdentityOperations identity = Mockito.mock(IdentityOperations.class);
        new CoachInitCommand(identity).initialize(new StringReader("GEER\ncoach@example.com\n雪道 password 123456\n"));
        verify(identity).createCoach("GEER", "coach@example.com", "雪道 password 123456");
    }
}
