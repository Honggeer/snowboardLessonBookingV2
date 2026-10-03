package com.geer.snowboard.v2.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.geer.snowboard.v2.identity.adapter.in.jobs.MailPoller;
import com.geer.snowboard.v2.media.adapter.in.jobs.MediaPoller;
import com.geer.snowboard.v2.scheduling.application.port.out.TimeWindowPreviewStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {"identity.mail.worker.enabled=false", "booking.mail.worker.enabled=false",
        "media.worker.enabled=false", "spring.session.jdbc.cleanup-cron=-"})
@ActiveProfiles("local")
@Testcontainers
class LocalApplicationStartupTest {

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("snowboard_v2_startup")
            .withUsername("snowboard_v2_test")
            .withPassword("test_password");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }

    @Autowired
    ApplicationContext context;

    @Test
    void localApplicationStartsWithRealDatabaseAndDemoAdapter() {
        assertThat(context.getBean(TimeWindowPreviewStore.class)).isNotNull();
    }

    @Test
    void temporaryDatabaseContextDoesNotStartAutomaticMailPolling() {
        assertThat(context.getBeansOfType(MailPoller.class)).isEmpty();
    }

    @Test
    void temporaryDatabaseContextDoesNotStartAutomaticMediaPolling() {
        assertThat(context.getBeansOfType(MediaPoller.class)).isEmpty();
    }

    @Test
    void temporaryDatabaseContextDoesNotScheduleSessionCleanup() {
        var repository = context.getBean(JdbcIndexedSessionRepository.class);
        assertThat(ReflectionTestUtils.getField(repository, "cleanupCron"))
                .isEqualTo(Scheduled.CRON_DISABLED);
    }
}
