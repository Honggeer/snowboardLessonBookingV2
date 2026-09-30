package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import com.geer.snowboard.v2.identity.application.port.in.RegisterCommand;
import com.geer.snowboard.v2.identity.application.port.out.VerificationMailSender;
import com.geer.snowboard.v2.identity.application.port.out.VerificationMailQueue;
import com.geer.snowboard.v2.identity.application.service.VerificationMailWorker;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = "identity.mail.worker.enabled=false")
@Testcontainers
class MailWorkerTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("mail_worker_test").withUsername("mail_worker_test").withPassword("test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
        registry.add("identity.public-url", () -> "http://localhost:5173");
    }
    @TestConfiguration static class MailOverride {
        @Bean @Primary FakeSender fakeSender() { return new FakeSender(); }
    }
    static class FakeSender implements VerificationMailSender {
        final List<String> links = new ArrayList<>();
        boolean fail;
        @Override public void send(String email, String link) {
            if (fail) throw new IllegalStateException("sandbox offline");
            links.add(link);
        }
    }
    @Autowired IdentityOperations identity;
    @Autowired VerificationMailWorker worker;
    @Autowired FakeSender sender;
    @Autowired JdbcTemplate jdbc;
    @Autowired VerificationMailQueue queue;

    @Test void sendsOnlyAfterCommitAndKeepsFailedDeliveryRetryable() {
        identity.register(new RegisterCommand("Mail", "零基础", "mail@api.example", "snowboard password 雪山 2026"), "127.0.0.2");
        sender.fail = true;
        worker.runOnce();
        assertThat(sender.links).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM identity_mail_task", String.class)).isEqualTo("PENDING");
        jdbc.update("UPDATE identity_mail_task SET next_attempt_at=?", Instant.now().minusSeconds(1));
        worker.runOnce();
        Instant nextRetry = jdbc.queryForObject("SELECT next_attempt_at FROM identity_mail_task",
                (rs, row) -> rs.getTimestamp(1).toInstant());
        assertThat(nextRetry).isAfter(Instant.now().plusSeconds(100));
        jdbc.update("UPDATE identity_mail_task SET next_attempt_at=?", Instant.now().minusSeconds(1));
        sender.fail = false;
        worker.runOnce();
        assertThat(sender.links).hasSize(1);
        assertThat(sender.links.getFirst()).contains("/#verify?token=");
        assertThat(jdbc.queryForObject("SELECT status FROM identity_mail_task", String.class)).isEqualTo("SENT");
    }

    @Test void neverSendsInvalidatedVerificationTask() {
        sender.links.clear();
        sender.fail = false;
        identity.register(new RegisterCommand("Replace", "入门", "replace@api.example", "snowboard password 雪山 2026"), "127.0.0.3");
        String oldId = jdbc.queryForObject("SELECT id FROM identity_verification WHERE account_id=(SELECT id FROM identity_account WHERE email_key='replace@api.example')", String.class);
        jdbc.update("UPDATE identity_verification SET created_at=? WHERE id=?", Instant.now().minusSeconds(61), oldId);
        identity.resend("replace@api.example", "127.0.0.3");
        worker.runOnce();
        assertThat(sender.links).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT status FROM identity_mail_task WHERE verification_id=?", String.class, oldId))
                .isEqualTo("SKIPPED");
        assertThat(identity.verify(sender.links.getFirst().split("token=")[1])).isTrue();
    }

    @Test void recoversAClaimLeftByAnInterruptedWorker() {
        sender.links.clear();
        sender.fail = false;
        identity.register(new RegisterCommand("Recover", "入门", "recover@api.example", "snowboard password 雪山 2026"), "127.0.0.4");
        var claim = queue.claim(Instant.now());
        assertThat(claim).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM identity_mail_task WHERE id=?", String.class, claim.id()))
                .isEqualTo("CLAIMED");
        jdbc.update("UPDATE identity_mail_task SET claim_until=? WHERE id=?", Instant.now().minusSeconds(1), claim.id());
        worker.runOnce();
        assertThat(sender.links).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT status FROM identity_mail_task WHERE id=?", String.class, claim.id()))
                .isEqualTo("SENT");
    }
}
