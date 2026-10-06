package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jayway.jsonpath.JsonPath;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties={"identity.mail.worker.enabled=false","booking.mail.worker.enabled=false","spring.session.jdbc.cleanup-cron=-"})
@AutoConfigureMockMvc
@Testcontainers
class LessonReminderApiTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("lesson_reminders").withUsername("reminder_test").withPassword("test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",mysql::getJdbcUrl); r.add("spring.datasource.username",mysql::getUsername);
        r.add("spring.datasource.password",mysql::getPassword);
        r.add("identity.verification-key",()->"a-private-test-key-with-at-least-32-bytes");
    }
    @TestConfiguration static class TestTime {
        @Bean @Primary Clock reminderClock() { return Clock.fixed(Instant.parse("2030-01-07T12:00:00Z"),ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired com.geer.snowboard.v2.bookings.application.port.in.BookingReminderOperations reminders;

    @Test void confirmationPlansTwoIndependentRemindersAtTheV1TimeAndReplayDoesNotDuplicate() throws Exception {
        var f=fixture();
        jdbc.update("UPDATE identity_account SET contact_phone='+14165550123' WHERE id=?",f.student());
        String id=booking(f,UUID.randomUUID().toString());
        for(int i=0;i<2;i++) mvc.perform(post("/api/coach/bookings/{id}/confirm",id)
                .with(user(f.coach()).roles("COACH")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=? AND event_type LIKE '%LESSON_REMINDER'",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT event_type FROM bookings_mail_task WHERE booking_id=?",String.class,id))
                .containsExactlyInAnyOrder("APPLICATION_RECEIVED","BOOKING_CONFIRMED","STUDENT_LESSON_REMINDER","COACH_LESSON_REMINDER");
        Instant start=jdbc.queryForObject("SELECT start_at_utc_snapshot FROM bookings_request WHERE id=?",(rs,row)->rs.getObject(1,LocalDateTime.class).toInstant(ZoneOffset.UTC),id);
        assertThat(jdbc.query("SELECT next_attempt_at FROM bookings_mail_task WHERE booking_id=? AND event_type LIKE '%LESSON_REMINDER'",(rs,row)->rs.getObject(1,LocalDateTime.class).toInstant(ZoneOffset.UTC),id))
                .containsOnly(start.minus(Duration.ofHours(40)));
        assertThat(jdbc.queryForList("SELECT recipient_account_id FROM bookings_mail_task WHERE booking_id=? AND event_type LIKE '%LESSON_REMINDER'",String.class,id))
                .containsExactlyInAnyOrder(f.student(),f.coach());
    }

    @Test void reminderWriteFailureRollsBackConfirmationAndOriginalConfirmationMail() throws Exception {
        var f=fixture(); jdbc.update("UPDATE identity_account SET contact_phone='+14165550123' WHERE id=?",f.student());
        String id=booking(f,UUID.randomUUID().toString());
        jdbc.execute("ALTER TABLE bookings_mail_task ADD CONSTRAINT ck_confirm_reminder_test CHECK (event_type<>'COACH_LESSON_REMINDER')");
        try {
            mvc.perform(post("/api/coach/bookings/{id}/confirm",id).with(user(f.coach()).roles("COACH")).with(csrf())).andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("SELECT status FROM bookings_request WHERE id=?",String.class,id)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?",String.class,f.slot())).isEqualTo("OPEN");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=?",Integer.class,id)).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE bookings_mail_task DROP CHECK ck_confirm_reminder_test"); }
    }
    @Test void concurrentConfirmationAndBackfillCreateExactlyOneReminderPerRecipient() throws Exception {
        var f=fixture(); jdbc.update("UPDATE identity_account SET contact_phone='+14165550123' WHERE id=?",f.student());
        String id=booking(f,UUID.randomUUID().toString());
        var gate=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await();return mvc.perform(post("/api/coach/bookings/{id}/confirm",id).with(user(f.coach()).roles("COACH")).with(csrf())).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{gate.await();reminders.backfill();return true;});
            gate.countDown(); assertThat(a.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200); b.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        reminders.backfill();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=? AND event_type LIKE '%LESSON_REMINDER'",Integer.class,id)).isEqualTo(2);
    }

    private record Fixture(String coach, String student, String course, String slot, String mountain) {}
    private Fixture fixture() throws Exception {
        String coach = coach(), student = account("STUDENT");
        String mountain = JsonPath.read(mvc.perform(post("/api/coach/mountains").with(user(coach).roles("COACH")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mountain " + UUID.randomUUID() + "\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String course = JsonPath.read(mvc.perform(post("/api/coach/courses").with(user(coach).roles("COACH")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"基础课\",\"priceAmount\":\"150.00\",\"currency\":\"CAD\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        int days = jdbc.queryForObject("SELECT COUNT(*) FROM scheduling_slot", Integer.class) + 10;
        String date = LocalDate.now(clock.withZone(ZoneId.of("America/Toronto"))).plusDays(days).toString();
        String slot = JsonPath.read(mvc.perform(post("/api/coach/availability/batches").with(user(coach).roles("COACH")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":[{\"localDate\":\"" + date + "\",\"startTime\":\"10:00\",\"endTime\":\"12:00\"}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.slots[0].id");
        return new Fixture(coach, student, course, slot, mountain);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder application(Fixture f, String key) {
        return post("/api/bookings").with(user(f.student()).roles("STUDENT")).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":\"" + f.course() + "\",\"slotId\":\"" + f.slot() + "\",\"mountainId\":\"" + f.mountain() + "\"}");
    }
    private String booking(Fixture f, String key) throws Exception {
        return JsonPath.read(mvc.perform(application(f, key)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }
    private String coach() { String id = jdbc.query("SELECT id FROM identity_account WHERE role='COACH'", rs -> rs.next() ? rs.getString(1) : null); return id == null ? account("COACH") : id; }
    private String account(String role) {
        String id = UUID.randomUUID().toString(), email = id + "@example.test";
        jdbc.update("INSERT INTO identity_account (id,email,email_key,name,level,role,password_hash,verified_at,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id, email, email, role.equals("COACH") ? "教练" : "学员", role.equals("COACH") ? null : "BEGINNER", role, "test-hash", Instant.now(), Instant.now());
        return id;
    }
}
