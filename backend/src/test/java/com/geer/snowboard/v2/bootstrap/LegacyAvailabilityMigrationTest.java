package com.geer.snowboard.v2.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class LegacyAvailabilityMigrationTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("availability_migration_test")
            .withUsername("availability_migration_test").withPassword("test_password");

    @Test
    void v7PreservesLegacySlotAndBookingWhileQuarantiningItsDay() throws Exception {
        var before = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration").target("4").load();
        before.migrate();
        String coach = UUID.randomUUID().toString();
        String student = UUID.randomUUID().toString();
        String course = UUID.randomUUID().toString();
        String slot = UUID.randomUUID().toString();
        String booking = UUID.randomUUID().toString();
        LocalDate date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(8);
        LocalDateTime start = LocalDateTime.ofInstant(Instant.now().plusSeconds(8 * 86400L), ZoneOffset.UTC)
                .withHour(14).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
            for (String account : new String[]{coach, student}) {
                try (var insert = connection.prepareStatement("""
                        INSERT INTO identity_account
                        (id,email,email_key,name,level,role,password_hash,verified_at,created_at)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """)) {
                    insert.setString(1, account);
                    insert.setString(2, account + "@example.test");
                    insert.setString(3, account + "@example.test");
                    insert.setString(4, account.equals(coach) ? "教练" : "学员");
                    insert.setString(5, account.equals(coach) ? null : "BEGINNER");
                    insert.setString(6, account.equals(coach) ? "COACH" : "STUDENT");
                    insert.setString(7, "test-hash");
                    insert.setObject(8, LocalDateTime.now());
                    insert.setObject(9, LocalDateTime.now());
                    insert.executeUpdate();
                }
            }
            try (var insert = connection.prepareStatement("""
                    INSERT INTO catalog_course
                    (id,coach_id,title,description,price_amount,currency,idempotency_key,request_fingerprint,created_at)
                    VALUES (?,?,?, ?,150.00,'CAD',?,?,CURRENT_TIMESTAMP(6))
                    """)) {
                insert.setString(1, course); insert.setString(2, coach); insert.setString(3, "旧课程");
                insert.setString(4, ""); insert.setString(5, UUID.randomUUID().toString());
                insert.setString(6, "a".repeat(64)); insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("""
                    INSERT INTO scheduling_slot
                    (id,coach_id,course_id,location,zone_id,local_date,start_at_utc,end_at_utc,status,
                     idempotency_key,request_fingerprint,created_at)
                    VALUES (?,?,?,?,?,?,?,?,'OPEN',?,?,CURRENT_TIMESTAMP(6))
                    """)) {
                insert.setString(1, slot); insert.setString(2, coach); insert.setString(3, course);
                insert.setString(4, "旧集合地点"); insert.setString(5, "America/Toronto");
                insert.setObject(6, date); insert.setObject(7, start); insert.setObject(8, end);
                insert.setString(9, UUID.randomUUID().toString()); insert.setString(10, "b".repeat(64));
                insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("""
                    INSERT INTO bookings_request
                    (id,slot_id,course_id,coach_id,student_id,student_name_snapshot,status,
                     course_title_snapshot,price_amount_snapshot,currency_snapshot,location_snapshot,
                     zone_id_snapshot,local_date_snapshot,start_at_utc_snapshot,end_at_utc_snapshot,
                     idempotency_key,request_fingerprint,created_at)
                    VALUES (?,?,?,?,?,?,'PENDING',?,150.00,'CAD',?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6))
                    """)) {
                insert.setString(1, booking); insert.setString(2, slot); insert.setString(3, course);
                insert.setString(4, coach); insert.setString(5, student); insert.setString(6, "旧学员");
                insert.setString(7, "旧课程"); insert.setString(8, "旧集合地点");
                insert.setString(9, "America/Toronto"); insert.setObject(10, date);
                insert.setObject(11, start); insert.setObject(12, end);
                insert.setString(13, UUID.randomUUID().toString()); insert.setString(14, "c".repeat(64));
                insert.executeUpdate();
            }
        }
        var after = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration").load();
        after.migrate();
        assertThat(after.info().current().getVersion().getVersion()).isEqualTo("12");
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT course_id,location FROM scheduling_slot WHERE id='" + slot + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(course);
                assertThat(rows.getString(2)).isEqualTo("旧集合地点");
            }
            try (var rows = statement.executeQuery("SELECT mountain_id,location_snapshot,price_amount_snapshot FROM bookings_request WHERE id='" + booking + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isNull();
                assertThat(rows.getString(2)).isEqualTo("旧集合地点");
                assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("150.00");
            }
            try (var rows = statement.executeQuery("SELECT legacy_review_required FROM scheduling_day WHERE coach_id='" + coach + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBoolean(1)).isTrue();
            }
        }
    }
}
