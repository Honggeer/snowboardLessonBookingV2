package com.geer.snowboard.v2.catalog;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class CourseCoverMigrationTest {
    @Container static final MySQLContainer mysql=new MySQLContainer("mysql:8.4")
            .withDatabaseName("cover_migration").withUsername("cover_test").withPassword("test_password");
    @Test void v9CourseKeepsItsFieldsAndGetsOptionalCenteredCover() throws Exception {
        Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword())
                .locations("classpath:db/migration").target("9").load().migrate();
        String coach=UUID.randomUUID().toString(),course=UUID.randomUUID().toString();
        try(var connection=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());var sql=connection.createStatement()) {
            sql.executeUpdate("INSERT INTO identity_account (id,email,email_key,name,role,password_hash,created_at) VALUES ('"+coach+"','coach@test.invalid','coach@test.invalid','Coach','COACH','unused',CURRENT_TIMESTAMP(6))");
            sql.executeUpdate("INSERT INTO catalog_course (id,coach_id,title,description,price_amount,currency,idempotency_key,request_fingerprint,created_at) VALUES ('"+course+"','"+coach+"','Original course','Original description',150.25,'CAD','old-key','old-fingerprint',CURRENT_TIMESTAMP(6))");
        }
        var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        try(var connection=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());var sql=connection.createStatement()) {
            try(var row=sql.executeQuery("SELECT * FROM catalog_course WHERE id='"+course+"'")) {
                assertThat(row.next()).isTrue(); assertThat(row.getString("title")).isEqualTo("Original course");
                assertThat(row.getBigDecimal("price_amount")).isEqualByComparingTo("150.25");
                assertThat(row.getString("request_fingerprint")).isEqualTo("old-fingerprint");
                assertThat(row.getString("cover_asset_id")).isNull();
                assertThat(row.getBigDecimal("cover_position_x")).isEqualByComparingTo("50");
                assertThat(row.getBigDecimal("cover_position_y")).isEqualByComparingTo("50");
            }
            assertThatThrownBy(()->sql.executeUpdate("UPDATE catalog_course SET cover_position_x=100.01 WHERE id='"+course+"'")).isInstanceOf(java.sql.SQLException.class);
        }
    }
}
