package com.geer.snowboard.v2.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.geer.snowboard.v2.scheduling.application.port.out.TimeWindowPreviewStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
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
}
