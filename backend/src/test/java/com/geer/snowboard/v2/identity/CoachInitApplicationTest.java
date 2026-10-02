package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.geer.snowboard.v2.SnowboardV2Application;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class CoachInitApplicationTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("coach_init_test").withUsername("coach_init_test").withPassword("test_password");

    @Test void nonWebCommandCreatesOnlyAPendingCoach() {
        InputStream original = System.in;
        System.setIn(new ByteArrayInputStream("GEER\ncoach-cli@example.test\nsnowboard password 雪山 2026\n"
                .getBytes(StandardCharsets.UTF_8)));
        try (var context = new SpringApplicationBuilder(SnowboardV2Application.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + mysql.getJdbcUrl(),
                        "--spring.datasource.username=" + mysql.getUsername(),
                        "--spring.datasource.password=" + mysql.getPassword(),
                        "--identity.verification-key=a-private-test-key-with-at-least-32-bytes",
                        "--identity.mail.worker.enabled=false",
                        "--booking.mail.worker.enabled=false",
                        "--spring.profiles.active=local",
                        "--identity.coach-init=true")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_account WHERE role='COACH' AND verified_at IS NULL",
                    Integer.class)).isEqualTo(1);
        } finally {
            System.setIn(original);
        }
    }
}
