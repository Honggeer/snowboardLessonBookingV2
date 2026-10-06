package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class LessonReminderMigrationTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("reminder_migration").withUsername("contact_test").withPassword("test_password");
    @Test void v11BookingPhoneAndPendingMailRemainIntactAfterV12() throws Exception {
        Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration").target("11").load().migrate();
        String coach = UUID.randomUUID().toString(), student = UUID.randomUUID().toString();
        String course = UUID.randomUUID().toString(), slot = UUID.randomUUID().toString(), booking = UUID.randomUUID().toString();
        try (var c = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()); var sql = c.createStatement()) {
            sql.executeUpdate("INSERT INTO identity_account (id,email,email_key,name,level,role,password_hash,verified_at,created_at) VALUES ('" + coach + "','coach@test.invalid','coach@test.invalid','Coach',NULL,'COACH','coach-hash',NOW(6),NOW(6)),('" + student + "','student@test.invalid','student@test.invalid','Student','BEGINNER','STUDENT','original-hash',NOW(6),NOW(6))");
            sql.executeUpdate("INSERT INTO catalog_course (id,coach_id,title,description,price_amount,currency,idempotency_key,request_fingerprint,created_at) VALUES ('" + course + "','" + coach + "','Original course','',150.25,'CAD','old-course-key','old-course-fingerprint',NOW(6))");
            sql.executeUpdate("INSERT INTO scheduling_slot (id,coach_id,zone_id,local_date,start_at_utc,end_at_utc,status,idempotency_key,request_fingerprint,created_at) VALUES ('" + slot + "','" + coach + "','America/Toronto','2027-01-10','2027-01-10 15:00:00','2027-01-10 17:00:00','OPEN','old-slot-key','old-slot-fingerprint',NOW(6))");
            sql.executeUpdate("INSERT INTO bookings_request (id,slot_id,course_id,coach_id,student_id,student_name_snapshot,status,course_title_snapshot,price_amount_snapshot,currency_snapshot,location_snapshot,zone_id_snapshot,local_date_snapshot,start_at_utc_snapshot,end_at_utc_snapshot,idempotency_key,request_fingerprint,created_at) VALUES ('" + booking + "','" + slot + "','" + course + "','" + coach + "','" + student + "','Student','PENDING','Original course',150.25,'CAD','Original mountain','America/Toronto','2027-01-10','2027-01-10 15:00:00','2027-01-10 17:00:00','old-booking-key','old-booking-fingerprint',NOW(6))");
            sql.executeUpdate("UPDATE identity_account SET contact_phone='+14165550123' WHERE id='"+student+"'");
            sql.executeUpdate("INSERT INTO bookings_mail_task (booking_id,event_type,recipient_account_id,status,attempts,next_attempt_at,created_at) VALUES ('"+booking+"','APPLICATION_RECEIVED','"+coach+"','PENDING',2,'2030-01-01 12:00:00','2029-12-31 12:00:00')");
        }
        var flyway = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()).locations("classpath:db/migration").target("12").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        try (var c = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()); var sql = c.createStatement()) {
            try (var row = sql.executeQuery("SELECT contact_phone,password_hash,credential_version,name FROM identity_account WHERE id='" + student + "'")) {
                assertThat(row.next()).isTrue(); assertThat(row.getString("contact_phone")).isEqualTo("+14165550123");
                assertThat(row.getString("password_hash")).isEqualTo("original-hash");
                assertThat(row.getLong("credential_version")).isZero(); assertThat(row.getString("name")).isEqualTo("Student");
            }
            try(var row=sql.executeQuery("SELECT lesson_reminder_planned_at FROM bookings_request WHERE id='"+booking+"'")) {
                assertThat(row.next()).isTrue();assertThat(row.getObject(1)).isNull();
            }
            try(var row=sql.executeQuery("SELECT status,attempts,next_attempt_at,reminder_start_at_utc FROM bookings_mail_task WHERE booking_id='"+booking+"'")) {
                assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("PENDING");assertThat(row.getInt(2)).isEqualTo(2);
                assertThat(row.getObject(3,java.time.LocalDateTime.class)).isEqualTo(java.time.LocalDateTime.parse("2030-01-01T12:00:00"));assertThat(row.getObject(4)).isNull();
            }
            assertThatThrownBy(()->sql.executeUpdate("INSERT INTO bookings_mail_task (booking_id,event_type,recipient_account_id,next_attempt_at,created_at) VALUES ('"+booking+"','STUDENT_LESSON_REMINDER','"+student+"',NOW(),NOW())")).isInstanceOf(java.sql.SQLException.class);
            try (var row = sql.executeQuery("SELECT status,course_title_snapshot,price_amount_snapshot,request_fingerprint FROM bookings_request WHERE id='" + booking + "'")) {
                assertThat(row.next()).isTrue(); assertThat(row.getString("status")).isEqualTo("PENDING");
                assertThat(row.getString("course_title_snapshot")).isEqualTo("Original course");
                assertThat(row.getBigDecimal("price_amount_snapshot")).isEqualByComparingTo("150.25");
                assertThat(row.getString("request_fingerprint")).isEqualTo("old-booking-fingerprint");
            }
        }
    }
}
