package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.assertThat;

import com.geer.snowboard.v2.bookings.application.port.in.BookingMailOperations;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailQueue;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailSender;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {
        "identity.mail.worker.enabled=false", "booking.mail.worker.enabled=false",
        "spring.session.jdbc.cleanup-cron=-", "identity.public-url=http://localhost:5173"})
@Testcontainers
class BookingMailWorkerTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("booking_mail_test").withUsername("booking_mail_test").withPassword("test_password");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }

    @TestConfiguration static class MailOverride {
        @Bean @Primary FakeSender fakeBookingSender() { return new FakeSender(); }
    }

    static class FakeSender implements BookingMailSender {
        record Message(String email, String subject, String body) {}
        final List<Message> messages = new ArrayList<>();
        boolean fail;
        @Override public void send(String email, String subject, String body) {
            if (fail) throw new IllegalStateException("smtp unavailable");
            messages.add(new Message(email, subject, body));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired BookingMailQueue queue;
    @Autowired BookingMailOperations worker;
    @Autowired FakeSender sender;
    @Autowired Clock clock;

    @BeforeEach void resetSender() {
        sender.messages.clear();
        sender.fail = false;
    }

    @Test void sendsTheCorrectRecipientDetailsAndDirectLinkForEachTransition() {
        Fixture fixture = booking("PENDING");
        queue.enqueue(fixture.bookingId(), BookingMailQueue.APPLICATION_RECEIVED, fixture.coachId(), clock.instant());
        worker.runOnce();
        assertThat(sender.messages).hasSize(1);
        assertThat(sender.messages.getFirst().email()).isEqualTo(fixture.coachId() + "@example.test");
        assertThat(sender.messages.getFirst().subject()).contains("新的预约申请");
        assertThat(sender.messages.getFirst().body()).contains("学员：测试学员", "课程：单板课程",
                "雪场：Blue Mountain", "America/Toronto",
                "http://localhost:5173/#/coach-applications/" + fixture.bookingId());
        assertThat(jdbc.queryForObject("SELECT status FROM bookings_mail_task WHERE booking_id=?",
                String.class, fixture.bookingId())).isEqualTo("SENT");

        jdbc.update("UPDATE bookings_request SET status='CONFIRMED' WHERE id=?", fixture.bookingId());
        queue.enqueue(fixture.bookingId(), BookingMailQueue.BOOKING_CONFIRMED, fixture.studentId(), clock.instant());
        worker.runOnce();
        assertThat(sender.messages).hasSize(2);
        assertThat(sender.messages.get(1).email()).isEqualTo(fixture.studentId() + "@example.test");
        assertThat(sender.messages.get(1).subject()).contains("已确认");
        assertThat(sender.messages.get(1).body()).contains("课程开始至少 24 小时前取消",
                "http://localhost:5173/#/my-bookings/" + fixture.bookingId());
    }

    @Test void retriesSmtpFailureThenMarksTheEighthFailureDead() {
        Fixture recovered = booking("PENDING");
        queue.enqueue(recovered.bookingId(), BookingMailQueue.APPLICATION_RECEIVED, recovered.coachId(), clock.instant());
        sender.fail = true;
        worker.runOnce();
        assertThat(jdbc.queryForObject("SELECT status FROM bookings_mail_task WHERE booking_id=?",
                String.class, recovered.bookingId())).isEqualTo("PENDING");
        Instant nextAttempt = jdbc.queryForObject("SELECT next_attempt_at FROM bookings_mail_task WHERE booking_id=?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), recovered.bookingId());
        assertThat(nextAttempt).isAfter(clock.instant().plusSeconds(50));
        jdbc.update("UPDATE bookings_mail_task SET next_attempt_at=? WHERE booking_id=?",
                clock.instant().minusSeconds(1), recovered.bookingId());
        sender.fail = false;
        worker.runOnce();
        assertThat(sender.messages).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT status FROM bookings_mail_task WHERE booking_id=?",
                String.class, recovered.bookingId())).isEqualTo("SENT");

        Fixture exhausted = booking("PENDING");
        queue.enqueue(exhausted.bookingId(), BookingMailQueue.APPLICATION_RECEIVED, exhausted.coachId(), clock.instant());
        sender.fail = true;
        for (int attempt = 0; attempt < 8; attempt++) {
            worker.runOnce();
            jdbc.update("UPDATE bookings_mail_task SET next_attempt_at=? WHERE booking_id=? AND status='PENDING'",
                    clock.instant().minusSeconds(1), exhausted.bookingId());
        }
        assertThat(jdbc.queryForObject("SELECT status FROM bookings_mail_task WHERE booking_id=?",
                String.class, exhausted.bookingId())).isEqualTo("DEAD");
        assertThat(jdbc.queryForObject("SELECT attempts FROM bookings_mail_task WHERE booking_id=?",
                Integer.class, exhausted.bookingId())).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT status FROM bookings_request WHERE id=?",
                String.class, exhausted.bookingId())).isEqualTo("PENDING");
    }

    @Test void skipsMailWhenTheCurrentBookingStateNoLongerMatches() {
        Fixture rejected = booking("PENDING");
        queue.enqueue(rejected.bookingId(), BookingMailQueue.APPLICATION_RECEIVED, rejected.coachId(), clock.instant());
        jdbc.update("UPDATE bookings_request SET status='REJECTED' WHERE id=?", rejected.bookingId());
        Fixture cancelled = booking("CONFIRMED");
        queue.enqueue(cancelled.bookingId(), BookingMailQueue.BOOKING_CONFIRMED, cancelled.studentId(), clock.instant());
        jdbc.update("UPDATE bookings_request SET status='CANCELLED_BY_STUDENT' WHERE id=?", cancelled.bookingId());
        worker.runOnce();
        assertThat(sender.messages).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE status='SKIPPED' AND booking_id IN (?,?)",
                Integer.class, rejected.bookingId(), cancelled.bookingId())).isEqualTo(2);
    }

    @Test void twoClaimersCannotOwnTheSameLeaseAndOldClaimsCannotCompleteNewOnes() throws Exception {
        Fixture fixture = booking("PENDING");
        queue.enqueue(fixture.bookingId(), BookingMailQueue.APPLICATION_RECEIVED, fixture.coachId(), clock.instant());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var first = pool.submit(() -> { gate.await(); return queue.claim(clock.instant()); });
            var second = pool.submit(() -> { gate.await(); return queue.claim(clock.instant()); });
            gate.countDown();
            BookingMailQueue.Task left = first.get();
            BookingMailQueue.Task right = second.get();
            assertThat(java.util.stream.Stream.of(left, right).filter(task -> task != null)).hasSize(1);
            BookingMailQueue.Task old = left == null ? right : left;
            jdbc.update("UPDATE bookings_mail_task SET claim_until=? WHERE id=?",
                    clock.instant().minusSeconds(1), old.id());
            BookingMailQueue.Task renewed = queue.claim(clock.instant());
            assertThat(renewed).isNotNull();
            assertThat(renewed.claimToken()).isNotEqualTo(old.claimToken());
            queue.sent(old, clock.instant());
            assertThat(jdbc.queryForObject("SELECT status FROM bookings_mail_task WHERE id=?",
                    String.class, old.id())).isEqualTo("CLAIMED");
            queue.sent(renewed, clock.instant());
            assertThat(jdbc.queryForObject("SELECT status FROM bookings_mail_task WHERE id=?",
                    String.class, old.id())).isEqualTo("SENT");
        }
    }

    private Fixture booking(String status) {
        String coachId = jdbc.query("SELECT id FROM identity_account WHERE role='COACH'",
                rs -> rs.next() ? rs.getString(1) : null);
        if (coachId == null) {
            coachId = UUID.randomUUID().toString();
            account(coachId, "COACH", null);
        }
        String studentId = UUID.randomUUID().toString();
        account(studentId, "STUDENT", "BEGINNER");
        String courseId = UUID.randomUUID().toString();
        String slotId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();
        LocalDate date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(5);
        Instant start = date.atTime(LocalTime.of(10, 0)).atZone(ZoneId.of("America/Toronto")).toInstant();
        Instant end = start.plusSeconds(7200);
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO catalog_course
                (id,coach_id,title,description,price_amount,currency,idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,'单板课程','',150.00,'CAD',?,REPEAT('a',64),?)
                """, courseId, coachId, UUID.randomUUID().toString(), now);
        jdbc.update("""
                INSERT INTO scheduling_slot
                (id,coach_id,course_id,location,zone_id,local_date,start_at_utc,end_at_utc,status,
                 idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,?,'Blue Mountain','America/Toronto',?,?,?,'OPEN',?,REPEAT('b',64),?)
                """, slotId, coachId, courseId, date, utc(start), utc(end), UUID.randomUUID().toString(), now);
        jdbc.update("""
                INSERT INTO bookings_request
                (id,slot_id,course_id,coach_id,student_id,student_name_snapshot,status,course_title_snapshot,
                 price_amount_snapshot,currency_snapshot,location_snapshot,zone_id_snapshot,local_date_snapshot,
                 start_at_utc_snapshot,end_at_utc_snapshot,idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,?,?,?,'测试学员',?,'单板课程',150.00,'CAD','Blue Mountain','America/Toronto',?,?,?,?,
                        REPEAT('c',64),?)
                """, bookingId, slotId, courseId, coachId, studentId, status, date, utc(start), utc(end),
                UUID.randomUUID().toString(), now);
        return new Fixture(bookingId, coachId, studentId);
    }

    private void account(String id, String role, String level) {
        jdbc.update("""
                INSERT INTO identity_account
                (id,email,email_key,name,level,role,password_hash,verified_at,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, id + "@example.test", id + "@example.test", role, level,
                role, "test-hash", clock.instant(), clock.instant());
    }

    private static LocalDateTime utc(Instant point) {
        return LocalDateTime.ofInstant(point, ZoneOffset.UTC);
    }

    private record Fixture(String bookingId, String coachId, String studentId) {}
}
