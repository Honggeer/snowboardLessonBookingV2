package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import com.geer.snowboard.v2.identity.application.port.in.PasswordRecoveryOperations;
import com.geer.snowboard.v2.identity.application.port.in.RegisterCommand;
import com.geer.snowboard.v2.identity.application.port.in.RateLimited;
import com.geer.snowboard.v2.identity.application.port.out.PasswordResetCodeCodec;
import com.geer.snowboard.v2.identity.application.port.out.PasswordResetMailSender;
import com.geer.snowboard.v2.identity.application.port.out.VerificationTokenCodec;
import com.geer.snowboard.v2.identity.application.service.PasswordResetMailWorker;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
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
class PasswordRecoveryFlowTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("recovery_flow_test").withUsername("recovery_flow_test").withPassword("test_password");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }

    static class MutableClock extends Clock {
        final AtomicLong offset = new AtomicLong();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.now().plusMillis(offset.get()); }
    }

    static class CapturingSender implements PasswordResetMailSender {
        final List<String> codes = new ArrayList<>();
        boolean failNext;
        @Override public void send(String email, String code) {
            if (failNext) { failNext = false; throw new IllegalStateException("SMTP unavailable"); }
            codes.add(code);
        }
    }

    @TestConfiguration
    static class Fakes {
        @Bean @Primary MutableClock recoveryClock() { return new MutableClock(); }
        @Bean @Primary CapturingSender resetSender() { return new CapturingSender(); }
    }

    @Autowired IdentityOperations identity;
    @Autowired PasswordRecoveryOperations recovery;
    @Autowired PasswordResetMailWorker worker;
    @Autowired VerificationTokenCodec verificationTokens;
    @Autowired PasswordResetCodeCodec codes;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired CapturingSender sender;

    @AfterEach void resetClock() { clock.offset.set(0); sender.codes.clear(); sender.failNext = false; }

    private String verifiedStudent(String email) {
        identity.register(new RegisterCommand("Rider", "入门", email, "old pass 123"), "192.0.2.11");
        String verificationId = jdbc.queryForObject("""
                SELECT v.id FROM identity_verification v JOIN identity_account a ON a.id=v.account_id
                WHERE a.email_key=? ORDER BY v.created_at DESC LIMIT 1
                """, String.class, email);
        assertThat(identity.verify(verificationTokens.issue(verificationId))).isTrue();
        return jdbc.queryForObject("SELECT id FROM identity_account WHERE email_key=?", String.class, email);
    }

    private String resetId(String email) {
        return jdbc.queryForObject("""
                SELECT r.id FROM identity_password_reset r JOIN identity_account a ON a.id=r.account_id
                WHERE a.email_key=? ORDER BY r.created_at DESC LIMIT 1
                """, String.class, email);
    }

    @Test void studentCanVerifyEmailCodeAndSetOnlyAConfirmedNewPassword() {
        String email = "student-" + UUID.randomUUID() + "@example.test";
        String accountId = verifiedStudent(email);
        recovery.request(email, "192.0.2.12");
        worker.runOnce();
        String code = codes.issue(resetId(email));
        assertThat(sender.codes).contains(code);
        assertThat(code).matches("[0-9]{8}");
        var grant = recovery.verify(email, code, "192.0.2.13");
        assertThat(grant).isNotNull();
        assertThat(identity.authenticate(email, "old pass 123", "192.0.2.14")).isNotNull();
        assertThatThrownBy(() -> recovery.complete(grant, "new pass 456", "different"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recovery.complete(grant, "1234567", "1234567"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recovery.complete(grant, "old pass 123", "old pass 123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(recovery.complete(grant, "new pass 456", "new pass 456")).isTrue();
        assertThat(recovery.complete(grant, "another pass 789", "another pass 789")).isFalse();
        assertThat(identity.authenticate(email, "old pass 123", "192.0.2.15")).isNull();
        assertThat(identity.authenticate(email, "new pass 456", "192.0.2.16")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT credential_version FROM identity_account WHERE id=?", Long.class, accountId))
                .isEqualTo(1L);
    }

    @Test void unknownAndUnverifiedEmailsCreateNoResetTaskAndCoachCanRecover() {
        String missing = "missing-" + UUID.randomUUID() + "@example.test";
        recovery.request(missing, "192.0.2.21");
        assertThat(resetCount(missing)).isZero();
        String unverified = "unverified-" + UUID.randomUUID() + "@example.test";
        identity.register(new RegisterCommand("Pending", "零基础", unverified, "old pass 123"), "192.0.2.22");
        recovery.request(unverified, "192.0.2.23");
        assertThat(resetCount(unverified)).isZero();
        String email = "coach-" + UUID.randomUUID() + "@example.test";
        identity.createCoach("Coach", email, "old pass 123");
        String id = jdbc.queryForObject("""
                SELECT v.id FROM identity_verification v JOIN identity_account a ON a.id=v.account_id
                WHERE a.email_key=?
                """, String.class, email);
        assertThat(identity.verify(verificationTokens.issue(id))).isTrue();
        recovery.request(email, "192.0.2.24");
        assertThat(recovery.complete(recovery.verify(email, codes.issue(resetId(email)), "192.0.2.25"),
                "new coach pass", "new coach pass")).isTrue();
        assertThat(identity.authenticate(email, "new coach pass", "192.0.2.26").role()).isEqualTo("COACH");
    }

    private int resetCount(String email) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM identity_password_reset r JOIN identity_account a ON a.id=r.account_id
                WHERE a.email_key=?
                """, Integer.class, email);
    }

    @Test void fiveWrongCodesBlockResetAndExpiredOrReplacedCodesFail() {
        String email = "attempts-" + UUID.randomUUID() + "@example.test";
        verifiedStudent(email);
        recovery.request(email, "192.0.2.31");
        String first = resetId(email);
        String correct = codes.issue(first);
        String wrong = correct.equals("00000000") ? "11111111" : "00000000";
        for (int i = 0; i < 5; i++) assertThat(recovery.verify(email, wrong, "192.0.2.32")).isNull();
        assertThat(jdbc.queryForObject("SELECT wrong_attempts FROM identity_password_reset WHERE id=?", Integer.class, first))
                .isEqualTo(5);
        assertThat(recovery.verify(email, correct, "192.0.2.32")).isNull();
        clock.offset.addAndGet(61_000);
        recovery.request(email, "192.0.2.33");
        String second = resetId(email);
        assertThat(second).isNotEqualTo(first);
        assertThat(recovery.verify(email, correct, "192.0.2.34")).isNull();
        var grant = recovery.verify(email, codes.issue(second), "192.0.2.35");
        assertThat(grant).isNotNull();
        clock.offset.addAndGet(601_000);
        assertThat(recovery.complete(grant, "new pass 123", "new pass 123")).isFalse();
    }

    @Test void simultaneousCompletionCanChangePasswordOnlyOnce() throws Exception {
        String email = "parallel-" + UUID.randomUUID() + "@example.test";
        verifiedStudent(email);
        recovery.request(email, "192.0.2.41");
        var grant = recovery.verify(email, codes.issue(resetId(email)), "192.0.2.42");
        assertThat(grant).isNotNull();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return recovery.complete(grant, "new pass 111", "new pass 111"); });
            var second = pool.submit(() -> { start.await(); return recovery.complete(grant, "new pass 222", "new pass 222"); });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test void failedMailIsRetriedAndInvalidatedMailIsSkipped() {
        String email = "mail-" + UUID.randomUUID() + "@example.test";
        verifiedStudent(email);
        recovery.request(email, "192.0.2.51");
        String first = resetId(email);
        sender.failNext = true;
        worker.runOnce();
        assertThat(jdbc.queryForObject("""
                SELECT status FROM identity_password_reset_mail_task WHERE reset_id=?
                """, String.class, first)).isEqualTo("PENDING");
        jdbc.update("UPDATE identity_password_reset_mail_task SET next_attempt_at=? WHERE reset_id=?",
                clock.instant().minusSeconds(1), first);
        worker.runOnce();
        assertThat(sender.codes).contains(codes.issue(first));
        clock.offset.addAndGet(61_000);
        recovery.request(email, "192.0.2.52");
        String second = resetId(email);
        jdbc.update("UPDATE identity_password_reset_mail_task SET status='PENDING',next_attempt_at=? WHERE reset_id=?",
                clock.instant().minusSeconds(1), first);
        sender.codes.clear();
        worker.runOnce();
        assertThat(jdbc.queryForObject("SELECT status FROM identity_password_reset_mail_task WHERE reset_id=?",
                String.class, first)).isEqualTo("SKIPPED");
        assertThat(sender.codes).contains(codes.issue(second)).doesNotContain(codes.issue(first));
    }

    @Test void requestAndVerificationRateLimitsPersistBlockedAttempts() {
        String email = "limit-" + UUID.randomUUID() + "@example.test";
        verifiedStudent(email);
        for (int i = 0; i < 3; i++) {
            recovery.request(email, "192.0.2.61");
            clock.offset.addAndGet(61_000);
        }
        assertThatThrownBy(() -> recovery.request(email, "192.0.2.61"))
                .isInstanceOf(RateLimited.class);
        assertThat(jdbc.queryForObject("SELECT attempts FROM identity_rate_limit WHERE rate_key=?", Integer.class,
                "reset:request:email:" + email)).isEqualTo(4);
        for (int i = 0; i < 30; i++) {
            assertThat(recovery.verify("missing-" + UUID.randomUUID() + "@example.test", "00000000", "192.0.2.62"))
                    .isNull();
        }
        assertThatThrownBy(() -> recovery.verify("missing@example.test", "00000000", "192.0.2.62"))
                .isInstanceOf(RateLimited.class);
        assertThat(jdbc.queryForObject("SELECT attempts FROM identity_rate_limit WHERE rate_key=?", Integer.class,
                "reset:verify:ip:192.0.2.62")).isEqualTo(31);
    }
}
