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
    void freshV2DatabaseHasVersionedBaselineAndNoBusinessTables() throws Exception {
        var flyway = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");

        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement();
             var tables = statement.executeQuery("SELECT table_name FROM information_schema.tables "
                     + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'")) {
            assertThat(tables.next()).isFalse();
        }
    }
}
