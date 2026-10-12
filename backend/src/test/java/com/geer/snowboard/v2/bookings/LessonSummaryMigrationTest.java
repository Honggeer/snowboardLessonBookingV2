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
class LessonSummaryMigrationTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("summary_migration").withUsername("migration_test").withPassword("test_password");

    @Test void v14PermanentlyMarksHistoricalLessonsAndPreservesAllOldMailEvents() {
        var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("13").load().migrate();
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
        for (String event : new String[]{"APPLICATION_RECEIVED", "BOOKING_CONFIRMED", "STUDENT_LESSON_REMINDER", "COACH_LESSON_REMINDER", "BOOKING_REJECTED"}) {
            jdbc.update("""
                    INSERT INTO bookings_mail_task (booking_id,event_type,recipient_account_id,status,attempts,next_attempt_at,created_at,reminder_start_at_utc)
                    VALUES (?,?,?,'PENDING',2,'2030-01-08 23:00:00','2029-12-31 12:00:00',?)
                    """, booking, event, event.startsWith("COACH") || event.equals("APPLICATION_RECEIVED") ? coach : student,
                    event.endsWith("REMINDER") ? start : null);
        }

        String old=UUID.randomUUID().toString(),oldSlot=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO scheduling_slot(id,coach_id,zone_id,local_date,start_at_utc,end_at_utc,status,idempotency_key,request_fingerprint,created_at) VALUES (?,?,'America/Toronto','2020-01-10','2020-01-10 15:00:00','2020-01-10 17:00:00','OPEN','old-summary-slot','fingerprint',NOW(6))",oldSlot,coach);
        jdbc.update("""
                INSERT INTO bookings_request(id,slot_id,course_id,coach_id,student_id,student_name_snapshot,status,
                course_title_snapshot,price_amount_snapshot,currency_snapshot,location_snapshot,zone_id_snapshot,local_date_snapshot,
                start_at_utc_snapshot,end_at_utc_snapshot,idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,?,?,?,'Student','CONFIRMED','Old course',150,'CAD','Old mountain','America/Toronto','2020-01-10',
                '2020-01-10 15:00:00','2020-01-10 17:00:00','old-summary-key','old-fingerprint',NOW(6))
                """,old,oldSlot,course,coach,student);
        jdbc.update("UPDATE bookings_request SET status='CONFIRMED' WHERE id=?",booking);
        var flyway=Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("14").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE attempts=2 AND status='PENDING' AND next_attempt_at='2030-01-08 23:00:00'",Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT lesson_summary_reminder_planned_at FROM bookings_request WHERE id=?",Object.class,old)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT lesson_summary_reminder_planned_at FROM bookings_request WHERE id=?",Object.class,booking)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE event_type='COACH_SUMMARY_REMINDER'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT course_title_snapshot FROM bookings_request WHERE id=?",String.class,old)).isEqualTo("Old course");
        assertThat(jdbc.queryForList("SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='bookings_request'",String.class))
                .contains("ix_booking_summary_plan","ix_booking_student_completed","ix_booking_coach_completed");
        assertThatThrownBy(()->jdbc.update("INSERT INTO bookings_lesson_summary VALUES (?,'PUBLISHED','a','','c',1,NOW(6),NOW(6))",old)).hasMessageContaining("ck_lesson_summary_state");
        assertThatThrownBy(()->jdbc.update("INSERT INTO bookings_lesson_summary VALUES (?,'DRAFT','','','',0,NOW(6),NULL)",old)).hasMessageContaining("ck_lesson_summary_version");
        assertThatThrownBy(()->jdbc.update("INSERT INTO bookings_lesson_summary VALUES (?,'DRAFT','','','',1,NOW(6),NULL)",UUID.randomUUID().toString())).hasMessageContaining("fk_lesson_summary_booking");
        assertThatThrownBy(()->jdbc.update("INSERT INTO bookings_lesson_summary VALUES (?,'DRAFT',?,'','',1,NOW(6),NULL)",old,"x".repeat(3001))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.update("INSERT INTO bookings_lesson_summary VALUES (?,'DRAFT',?,'','',1,NOW(6),NULL)",old,"🏂".repeat(3000));
        assertThatThrownBy(()->insertTask(jdbc,booking,coach,"COACH_SUMMARY_REMINDER",null)).hasMessageContaining("ck_booking_summary_end");
        jdbc.update("INSERT INTO bookings_mail_task(booking_id,event_type,recipient_account_id,next_attempt_at,summary_end_at_utc,created_at) VALUES (?,'COACH_SUMMARY_REMINDER',?,'2030-01-10 17:00:00','2030-01-10 17:00:00',NOW(6))",booking,coach);
        insertTask(jdbc,old,student,"STUDENT_SUMMARY_PUBLISHED",null);
        assertThatThrownBy(()->jdbc.update("UPDATE bookings_mail_task SET summary_end_at_utc=NOW(6) WHERE event_type='STUDENT_SUMMARY_PUBLISHED'")).hasMessageContaining("ck_booking_summary_end");
        assertThatThrownBy(()->insertTask(jdbc,old,student,"UNKNOWN",null)).hasMessageContaining("ck_booking_mail_event");
        Object marker=jdbc.queryForObject("SELECT lesson_summary_reminder_planned_at FROM bookings_request WHERE id=?",Object.class,old);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT lesson_summary_reminder_planned_at FROM bookings_request WHERE id=?",Object.class,old)).isEqualTo(marker);
    }
    private static void insertTask(JdbcTemplate jdbc,String booking,String recipient,String event,LocalDateTime start) {
        jdbc.update("INSERT INTO bookings_mail_task(booking_id,event_type,recipient_account_id,next_attempt_at,created_at,reminder_start_at_utc) VALUES (?,?,?,NOW(6),NOW(6),?)",booking,event,recipient,start);
    }
}
