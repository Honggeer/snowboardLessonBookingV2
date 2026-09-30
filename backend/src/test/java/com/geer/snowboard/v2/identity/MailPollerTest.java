package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geer.snowboard.v2.identity.adapter.in.jobs.MailPoller;
import com.geer.snowboard.v2.identity.application.port.in.PasswordResetMailOperations;
import com.geer.snowboard.v2.identity.application.port.in.VerificationMailOperations;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class MailPollerTest {
    @Test
    void databaseFailureRemainsVisibleToSchedulerErrorHandling() {
        var outage = new DataAccessResourceFailureException("database unavailable");
        VerificationMailOperations verification = new VerificationMailOperations() {
            @Override public void runOnce() { throw outage; }
            @Override public void cleanup() {}
        };
        PasswordResetMailOperations reset = new PasswordResetMailOperations() {
            @Override public void runOnce() {}
            @Override public void cleanup() {}
        };

        assertThatThrownBy(new MailPoller(verification, reset)::poll).isSameAs(outage);
    }
}
