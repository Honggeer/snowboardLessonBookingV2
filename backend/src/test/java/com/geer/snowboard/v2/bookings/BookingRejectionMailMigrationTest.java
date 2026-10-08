package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class BookingRejectionMailMigrationTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("rejection_migration").withUsername("migration_test").withPassword("test_password");

    @Test void v13PreservesOldTasksAndAllowsOnlyValidRejectionAndReminderEvents() {
        var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("12").load().migrate();
        String coach = UUID.randomUUID().toString(), student = UUID.randomUUID().toString();
        String course = UUID.randomUUID().toString(), slot = UUID.randomUUID().toString(), booking = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO identity_account (id,email,email_key,name,level,role,password_hash,verified_at,created_at)
                VALUES (?,'coach@test.invalid','coach@test.invalid','Coach',NULL,'COACH','coach-hash',NOW(6),NOW(6)),
                       (?,'student@test.invalid','student@test.invalid','Student','BEGINNER','STUDENT','student-hash',NOW(6),NOW(6))
                """, coach, student);
        jdbc.update("""
                INSERT INTO catalog_course (id,coach_id,title,description,price_amount,currency,idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,'Original course','',150.25,'CAD','course-key','course-fingerprint',NOW(6))
                """, course, coach);
        jdbc.update("""
                INSERT INTO scheduling_slot (id,coach_id,zone_id,local_date,start_at_utc,end_at_utc,status,idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,'America/Toronto','2030-01-10','2030-01-10 15:00:00','2030-01-10 17:00:00','OPEN','slot-key','slot-fingerprint',NOW(6))
                """, slot, coach);
        jdbc.update("""
                INSERT INTO bookings_request (id,slot_id,course_id,coach_id,student_id,student_name_snapshot,status,decision_reason,
                    course_title_snapshot,price_amount_snapshot,currency_snapshot,location_snapshot,zone_id_snapshot,local_date_snapshot,
                    start_at_utc_snapshot,end_at_utc_snapshot,idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,?,?,?,'Student','REJECTED','Original reason','Original course',150.25,'CAD','Original mountain',
                    'America/Toronto','2030-01-10','2030-01-10 15:00:00','2030-01-10 17:00:00','booking-key','booking-fingerprint',NOW(6))
                """, booking, slot, course, coach, student);
        LocalDateTime start = LocalDateTime.parse("2030-01-10T15:00:00");
        for (String event : new String[]{"APPLICATION_RECEIVED", "BOOKING_CONFIRMED", "STUDENT_LESSON_REMINDER", "COACH_LESSON_REMINDER"}) {
            jdbc.update("""
                    INSERT INTO bookings_mail_task (booking_id,event_type,recipient_account_id,status,attempts,next_attempt_at,created_at,reminder_start_at_utc)
                    VALUES (?,?,?,'PENDING',2,'2030-01-08 23:00:00','2029-12-31 12:00:00',?)
                    """, booking, event, event.startsWith("COACH") || event.equals("APPLICATION_RECEIVED") ? coach : student,
                    event.endsWith("REMINDER") ? start : null);
        }
        var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("13").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE attempts=2 AND status='PENDING' AND next_attempt_at='2030-01-08 23:00:00'",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT decision_reason FROM bookings_request WHERE id=?", String.class, booking)).isEqualTo("Original reason");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE event_type='BOOKING_REJECTED'", Integer.class)).isZero();
        insertTask(jdbc, booking, student, "BOOKING_REJECTED", null);
        assertThatThrownBy(() -> insertTask(jdbc, booking, student, "BOOKING_REJECTED", null)).hasMessageContaining("uq_booking_mail_event");
        jdbc.update("DELETE FROM bookings_mail_task WHERE event_type='BOOKING_REJECTED'");
        assertThatThrownBy(() -> insertTask(jdbc, booking, student, "BOOKING_REJECTED", start)).hasMessageContaining("ck_booking_reminder_start");
        assertThatThrownBy(() -> insertTask(jdbc, booking, student, "UNKNOWN", null)).hasMessageContaining("ck_booking_mail_event");
        jdbc.update("DELETE FROM bookings_mail_task WHERE event_type IN ('STUDENT_LESSON_REMINDER','COACH_LESSON_REMINDER')");
        for (String event : new String[]{"STUDENT_LESSON_REMINDER", "COACH_LESSON_REMINDER"})
            assertThatThrownBy(() -> insertTask(jdbc, booking, student, event, null)).hasMessageContaining("ck_booking_reminder_start");
    }

    private static void insertTask(JdbcTemplate jdbc, String booking, String student, String event, LocalDateTime start) {
        jdbc.update("""
                INSERT INTO bookings_mail_task (booking_id,event_type,recipient_account_id,next_attempt_at,created_at,reminder_start_at_utc)
                VALUES (?,?,?,NOW(6),NOW(6),?)
                """, booking, event, student, start);
    }
}
