package com.geer.snowboard.v2.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class FoundationMigrationTest {

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("snowboard_v2_test")
            .withUsername("snowboard_v2_test")
            .withPassword("test_password");

    @Test
    void freshV2DatabaseHasIdentitySessionRecoveryAndBookingTables() throws Exception {
        var flyway = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("9");

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement();
             var tables = statement.executeQuery("SELECT table_name FROM information_schema.tables "
                     + "WHERE table_schema = DATABASE() AND table_name IN "
                     + "('identity_account','identity_verification','identity_mail_task','identity_rate_limit',"
                     + "'SPRING_SESSION','SPRING_SESSION_ATTRIBUTES',"
                     + "'identity_password_reset','identity_password_reset_mail_task',"
                     + "'catalog_course','scheduling_coach_guard','scheduling_slot',"
                     + "'scheduling_mountain','scheduling_day','scheduling_batch',"
                     + "'bookings_student_guard','bookings_request','bookings_mail_task')")) {
            int count = 0;
            while (tables.next()) count++;
            assertThat(count).isEqualTo(17);
        }
    }
}
