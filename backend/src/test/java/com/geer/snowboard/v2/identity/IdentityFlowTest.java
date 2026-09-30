package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import com.geer.snowboard.v2.identity.application.port.in.RegisterCommand;
import com.geer.snowboard.v2.identity.application.port.out.VerificationTokenCodec;
import com.geer.snowboard.v2.identity.application.service.IdentityService;
import com.geer.snowboard.v2.identity.application.port.in.RateLimited;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = "identity.mail.worker.enabled=false")
@Testcontainers
class IdentityFlowTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("identity_test").withUsername("identity_test").withPassword("test_password");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }

    @Autowired IdentityOperations identity;
    @Autowired VerificationTokenCodec tokens;
    @Autowired JdbcTemplate jdbc;

    @Test
    void registersOnlyStudentVerifiesOnceAndThenAuthenticates() {
        RegisterCommand input = new RegisterCommand("Geer", "入门", "Geer@Example.COM", "snowboard password 雪山 2026");
        identity.register(input, "127.0.0.1");
        identity.register(input, "127.0.0.1");
        assertThat(jdbc.queryForObject("select count(*) from identity_account where email_key=?", Integer.class, "geer@example.com")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select role from identity_account where email_key=?", String.class, "geer@example.com")).isEqualTo("STUDENT");
        assertThat(identity.authenticate(input.email(), input.password(), "127.0.0.1")).isNull();
        String tokenId = jdbc.queryForObject("select id from identity_verification where account_id=(select id from identity_account where email_key='geer@example.com')", String.class);
        String token = tokens.issue(tokenId);
        assertThat(identity.verify(tokenId + ".forged")).isFalse();
        assertThat(identity.verify(token)).isTrue();
        assertThat(identity.verify(token)).isFalse();
        assertThat(identity.authenticate(input.email(), "wrong password", "127.0.0.1")).isNull();
        assertThat(identity.authenticate(input.email(), input.password(), "127.0.0.1")).isNotNull();
    }

    @Test
    void concurrentRegistrationKeepsOneAccountAndOneVerification() throws Exception {
        RegisterCommand input = new RegisterCommand("Rider", "零基础", "same@api.example", "snowboard password 雪山 2026");
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { start.await(); identity.register(input, "127.0.0.8"); return true; });
            var second = workers.submit(() -> { start.await(); identity.register(input, "127.0.0.8"); return true; });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_account WHERE email_key='same@api.example'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_verification WHERE account_id=(SELECT id FROM identity_account WHERE email_key='same@api.example')", Integer.class)).isEqualTo(1);
    }

    @Test
    void resendInvalidatesOldLinkAndExpiredLinkCannotVerify() {
        identity.register(new RegisterCommand("Again", "进阶", "again@api.example", "snowboard password 雪山 2026"), "127.0.0.9");
        String oldId = jdbc.queryForObject("SELECT id FROM identity_verification WHERE account_id=(SELECT id FROM identity_account WHERE email_key='again@api.example')", String.class);
        jdbc.update("UPDATE identity_verification SET created_at=? WHERE id=?", Instant.now().minusSeconds(61), oldId);
        identity.resend("again@api.example", "127.0.0.9");
        assertThat(identity.verify(tokens.issue(oldId))).isFalse();
        String newId = jdbc.queryForObject("SELECT id FROM identity_verification WHERE account_id=(SELECT id FROM identity_account WHERE email_key='again@api.example') AND invalidated_at IS NULL", String.class);
        jdbc.update("UPDATE identity_verification SET expires_at=? WHERE id=?", Instant.now().minusSeconds(1), newId);
        assertThat(identity.verify(tokens.issue(newId))).isFalse();
    }

    @Test
    void limitsRepeatedLoginFailuresAndResendsForUnknownAddress() {
        for (int i = 0; i < 5; i++) assertThat(identity.authenticate("unknown@api.example", "wrong", "127.0.0.10")).isNull();
        assertThatThrownBy(() -> identity.authenticate("unknown@api.example", "wrong", "127.0.0.10"))
                .isInstanceOf(RateLimited.class);
        for (int i = 0; i < 3; i++) identity.resend("absent@api.example", "127.0.0.11");
        assertThatThrownBy(() -> identity.resend("absent@api.example", "127.0.0.11"))
                .isInstanceOf(RateLimited.class);
        for (int i = 0; i < 30; i++) {
            assertThat(identity.authenticate("unknown-" + i + "@api.example", "wrong", "127.0.0.13")).isNull();
        }
        assertThatThrownBy(() -> identity.authenticate("another@api.example", "wrong", "127.0.0.13"))
                .isInstanceOf(RateLimited.class);
        for (int i = 0; i < 10; i++) identity.resend("absent-" + i + "@api.example", "127.0.0.14");
        assertThatThrownBy(() -> identity.resend("more-absent@api.example", "127.0.0.14"))
                .isInstanceOf(RateLimited.class);
    }

    @Test
    void coachIsUniqueAndNeedsEmailVerificationBeforeLogin() {
        identity.createCoach("GEER", "coach@api.example", "snowboard password 雪山 2026");
        assertThatThrownBy(() -> identity.createCoach("Other", "other-coach@api.example", "snowboard password 雪山 2026"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(identity.authenticate("coach@api.example", "snowboard password 雪山 2026", "127.0.0.12")).isNull();
        String id = jdbc.queryForObject("SELECT id FROM identity_verification WHERE account_id=(SELECT id FROM identity_account WHERE email_key='coach@api.example')", String.class);
        assertThat(identity.verify(tokens.issue(id))).isTrue();
        assertThat(identity.authenticate("coach@api.example", "snowboard password 雪山 2026", "127.0.0.12").role())
                .isEqualTo("COACH");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_account WHERE role='COACH'", Integer.class)).isEqualTo(1);
    }
}
