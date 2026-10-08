package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.geer.snowboard.v2.identity.application.port.out.PasswordHashes;
import com.geer.snowboard.v2.bookings.application.port.in.BookingMailOperations;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailSender;
import com.geer.snowboard.v2.bookings.adapter.out.mail.SmtpBookingMailSender;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.containers.GenericContainer;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        "identity.mail.worker.enabled=false", "booking.mail.worker.enabled=false", "spring.session.jdbc.cleanup-cron=-"})
@AutoConfigureMockMvc
@Testcontainers
class BookingCoreApiTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("booking_core_test").withUsername("booking_core_test").withPassword("test_password");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHashes passwords;
    @Autowired BookingMailOperations mailWorker;
    @Autowired TestMailSender testMailSender;
    private final Map<String, String> slotCourses = new ConcurrentHashMap<>();
    private final Map<String, String> coachMountains = new ConcurrentHashMap<>();

    @TestConfiguration static class MailOverride {
        @Bean @Primary TestMailSender testMailSender() { return new TestMailSender(); }
    }

    static class TestMailSender implements BookingMailSender {
        BookingMailSender delegate;
        @Override public void send(String email, String subject, String body) {
            if (delegate == null) throw new IllegalStateException("Test SMTP is not configured");
            delegate.send(email, subject, body);
        }
    }

    @Test
    void manualAndBothAutomaticRejectionsActuallyReachOnlyTheirStudentsInMailpit() throws Exception {
        jdbc.update("UPDATE bookings_mail_task SET status='SKIPPED' WHERE status='PENDING'");
        try (var mailpit = new GenericContainer<>("ghcr.io/axllent/mailpit:v1.31.3").withExposedPorts(1025, 8025)) {
            mailpit.start();
            var smtp = new JavaMailSenderImpl();
            smtp.setHost(mailpit.getHost());
            smtp.setPort(mailpit.getMappedPort(1025));
            var factory = new StaticListableBeanFactory();
            factory.addBean("mail", smtp);
            testMailSender.delegate = new SmtpBookingMailSender(factory.getBeanProvider(JavaMailSender.class),
                    new MockEnvironment().withProperty("identity.mail.from", "geer@example.test"));

            String coach = account("COACH", null), course = publishCourse(coach);
            String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(12).toString();
            String manualStudent = account("STUDENT", "BEGINNER");
            String manual = apply(manualStudent, publishSlot(coach, course, date, "10:00"));
            rejectBooking(coach, manual, "时间不合适");
            String slot = publishSlot(coach, course, date, "12:00");
            String chosenStudent = account("STUDENT", "BEGINNER"), sameSlotStudent = account("STUDENT", "NOVICE");
            String chosen = apply(chosenStudent, slot), sameSlot = apply(sameSlotStudent, slot);
            String otherMountainStudent = account("STUDENT", "BEGINNER");
            String otherMountain = apply(otherMountainStudent, publishSlot(coach, course, date, "14:00"), createMountain(coach));
            confirmBooking(coach, chosen);
            mailWorker.runOnce();

            var expectedBookings = Map.of(manualStudent, manual, sameSlotStudent, sameSlot, otherMountainStudent, otherMountain);
            var expectedReasons = Map.of(manualStudent, "时间不合适", sameSlotStudent, "该时段已确认给其他学员",
                    otherMountainStudent, "当天已确认在其他雪场授课");
            var client = HttpClient.newHttpClient();
            String api = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
            String list = client.send(HttpRequest.newBuilder(URI.create(api + "/api/v1/messages")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            assertThat((Integer) JsonPath.read(list, "$.total")).isEqualTo(4);
            List<String> ids = JsonPath.read(list, "$.messages[*].ID");
            for (String id : ids) {
                String message = client.send(HttpRequest.newBuilder(URI.create(api + "/api/v1/message/" + id)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body();
                String recipient = JsonPath.read(message, "$.To[0].Address");
                String student = recipient.replace("@example.test", "");
                String subject = JsonPath.read(message, "$.Subject"), body = JsonPath.read(message, "$.Text");
                if (student.equals(chosenStudent)) {
                    assertThat(subject).isEqualTo("GEER 课程预约已确认");
                    assertThat(body).contains("/#/my-bookings/" + chosen);
                } else {
                    assertThat(expectedBookings).containsKey(student);
                    assertThat(subject).isEqualTo("GEER 预约申请未通过");
                    assertThat(body).contains("拒绝原因：" + expectedReasons.get(student),
                            "/#/my-bookings/" + expectedBookings.get(student), "America/Toronto", "课程：", "雪场：")
                            .doesNotContain(chosenStudent, "@example.test");
                }
            }
            for (String booking : expectedBookings.values())
                assertThat(jdbc.queryForObject("SELECT status FROM bookings_mail_task WHERE booking_id=? AND event_type='BOOKING_REJECTED'",
                        String.class, booking)).isEqualTo("SENT");
        } finally {
            testMailSender.delegate = null;
        }
    }

    @Test
    void concurrentCancellationAndRejectionCreateOnlyTheNotificationMatchingTheFinalDecision() throws Exception {
        String coach = account("COACH", null), student = account("STUDENT", "BEGINNER");
        String slot = publishSlot(coach, publishCourse(coach),
                LocalDate.now(ZoneId.of("America/Toronto")).plusDays(10).toString(), "10:00");
        String booking = apply(student, slot);
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var rejection = pool.submit(() -> {
                gate.await();
                return mvc.perform(post("/api/coach/bookings/{id}/reject", booking).with(user(coach).roles("COACH")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"时间不合适\"}"))
                        .andReturn().getResponse().getStatus();
            });
            var cancellation = pool.submit(() -> {
                gate.await();
                return mvc.perform(post("/api/bookings/{id}/cancel", booking).with(user(student).roles("STUDENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andReturn().getResponse().getStatus();
            });
            gate.countDown();
            assertThat(List.of(rejection.get(10, java.util.concurrent.TimeUnit.SECONDS), cancellation.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        String result = jdbc.queryForObject("SELECT status FROM bookings_request WHERE id=?", String.class, booking);
        assertThat(result).isIn("REJECTED", "CANCELLED_BY_STUDENT");
        assertThat(rejectionMailCount(booking)).isEqualTo(result.equals("REJECTED") ? 1 : 0);
    }

    @Test
    void manualRejectionCreatesOneStudentMailAndDoesNotRecreateCleanedTasks() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        String slot = publishSlot(coach, publishCourse(coach),
                LocalDate.now(ZoneId.of("America/Toronto")).plusDays(2).toString(), "10:00");
        String booking = apply(student, slot);
        rejectBooking(coach, booking, "  时间不合适  ");
        assertThat(rejectionMailCount(booking)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recipient_account_id FROM bookings_mail_task WHERE booking_id=? AND event_type='BOOKING_REJECTED'",
                String.class, booking)).isEqualTo(student);
        assertThat(jdbc.queryForObject("SELECT decision_reason FROM bookings_request WHERE id=?", String.class, booking))
                .isEqualTo("时间不合适");
        rejectBooking(coach, booking, "重复操作");
        assertThat(rejectionMailCount(booking)).isEqualTo(1);
        mvc.perform(get("/api/bookings/{id}", booking).with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/bookings/{id}", booking).with(user(account("STUDENT", "NOVICE")).roles("STUDENT")))
                .andExpect(status().isNotFound());
        jdbc.update("DELETE FROM bookings_mail_task WHERE booking_id=? AND event_type='BOOKING_REJECTED'", booking);
        rejectBooking(coach, booking, "清理后重放");
        assertThat(rejectionMailCount(booking)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, slot)).isEqualTo("OPEN");
    }

    @Test
    void confirmationNotifiesOnlyTheApplicationsActuallyAutoRejected() throws Exception {
        String coach = account("COACH", null);
        String course = publishCourse(coach);
        String mountain = ensureMountain(coach);
        String otherMountain = createMountain(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(4).toString();
        String chosenSlot = publishSlot(coach, course, date, "10:00");
        String chosen = apply(account("STUDENT", "BEGINNER"), chosenSlot);
        String competitorStudent = account("STUDENT", "NOVICE");
        String competitor = apply(competitorStudent, chosenSlot);
        String otherCompetitor = apply(account("STUDENT", "NOVICE"), chosenSlot, otherMountain);
        String sameMountain = apply(account("STUDENT", "BEGINNER"), publishSlot(coach, course, date, "12:00"));
        String otherMountainStudent = account("STUDENT", "BEGINNER");
        String otherMountainBooking = apply(otherMountainStudent, publishSlot(coach, course, date, "14:00"), otherMountain);
        String terminalSlot = publishSlot(coach, course, date, "16:00");
        String oldRejected = apply(account("STUDENT", "BEGINNER"), terminalSlot, otherMountain);
        jdbc.update("UPDATE bookings_request SET status='REJECTED',decision_reason='历史拒绝',decided_at=? WHERE id=?",
                Instant.now(), oldRejected);
        String cancelled = apply(account("STUDENT", "BEGINNER"), terminalSlot, otherMountain);
        jdbc.update("UPDATE bookings_request SET status='CANCELLED_BY_STUDENT',decided_at=? WHERE id=?", Instant.now(), cancelled);
        String otherDate = apply(account("STUDENT", "BEGINNER"), publishSlot(coach, course,
                LocalDate.now(ZoneId.of("America/Toronto")).plusDays(8).toString(), "10:00"), otherMountain);

        confirmBooking(coach, chosen);
        for (String rejected : List.of(competitor, otherCompetitor, otherMountainBooking))
            assertThat(rejectionMailCount(rejected)).as(rejected).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recipient_account_id FROM bookings_mail_task WHERE booking_id=? AND event_type='BOOKING_REJECTED'",
                String.class, competitor)).isEqualTo(competitorStudent);
        assertThat(jdbc.queryForObject("SELECT recipient_account_id FROM bookings_mail_task WHERE booking_id=? AND event_type='BOOKING_REJECTED'",
                String.class, otherMountainBooking)).isEqualTo(otherMountainStudent);
        for (String sameSlot : List.of(competitor, otherCompetitor))
            assertThat(jdbc.queryForObject("SELECT decision_reason FROM bookings_request WHERE id=?", String.class, sameSlot))
                    .isEqualTo("该时段已确认给其他学员");
        assertThat(jdbc.queryForObject("SELECT decision_reason FROM bookings_request WHERE id=?", String.class, otherMountainBooking))
                .isEqualTo("当天已确认在其他雪场授课");
        for (String unaffected : List.of(chosen, sameMountain, otherDate, oldRejected, cancelled))
            assertThat(rejectionMailCount(unaffected)).as(unaffected).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE id IN (?,?) AND status='PENDING'",
                Integer.class, sameMountain, otherDate)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT locked_mountain_id FROM scheduling_day WHERE coach_id=? AND local_date=?",
                String.class, coach, LocalDate.parse(date))).isEqualTo(mountain);
        confirmBooking(coach, chosen);
        assertThat(rejectionMailCount(competitor)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=? AND event_type='BOOKING_CONFIRMED'",
                Integer.class, chosen)).isEqualTo(1);
    }

    @Test
    void rejectionMailInsertFailureRollsBackManualAndAutomaticDecisions() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        String course = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(6).toString();
        String manualSlot = publishSlot(coach, course, date, "10:00");
        String manual = apply(student, manualSlot);
        String chosenSlot = publishSlot(coach, course, date, "12:00");
        String chosen = apply(account("STUDENT", "BEGINNER"), chosenSlot);
        String competitor = apply(student, chosenSlot);
        String otherMountain = createMountain(coach);
        String other = apply(account("STUDENT", "BEGINNER"), publishSlot(coach, course, date, "14:00"), otherMountain);
        jdbc.execute("ALTER TABLE bookings_mail_task ADD CONSTRAINT ck_rejection_failure_test CHECK "
                + "(event_type <> 'BOOKING_REJECTED' OR recipient_account_id <> '" + student + "')");
        try {
            mvc.perform(post("/api/coach/bookings/{id}/reject", manual).with(user(coach).roles("COACH")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"无法授课\"}"))
                    .andExpect(status().isServiceUnavailable());
            mvc.perform(post("/api/coach/bookings/{id}/confirm", chosen).with(user(coach).roles("COACH")).with(csrf()))
                    .andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE id IN (?,?,?,?) AND status='PENDING'",
                    Integer.class, manual, chosen, competitor, other)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, chosenSlot)).isEqualTo("OPEN");
            assertThat(jdbc.queryForObject("SELECT locked_mountain_id FROM scheduling_day WHERE coach_id=? AND local_date=?",
                    String.class, coach, LocalDate.parse(date))).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id IN (?,?,?,?) AND event_type <> 'APPLICATION_RECEIVED'",
                    Integer.class, manual, chosen, competitor, other)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE bookings_mail_task DROP CHECK ck_rejection_failure_test");
        }
        String otherStudent = jdbc.queryForObject("SELECT student_id FROM bookings_request WHERE id=?", String.class, other);
        jdbc.execute("ALTER TABLE bookings_mail_task ADD CONSTRAINT ck_rejection_failure_test CHECK "
                + "(event_type <> 'BOOKING_REJECTED' OR recipient_account_id <> '" + otherStudent + "')");
        try {
            mvc.perform(post("/api/coach/bookings/{id}/confirm", chosen).with(user(coach).roles("COACH")).with(csrf()))
                    .andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE id IN (?,?,?,?) AND status='PENDING'",
                    Integer.class, manual, chosen, competitor, other)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id IN (?,?,?,?) AND event_type <> 'APPLICATION_RECEIVED'",
                    Integer.class, manual, chosen, competitor, other)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, chosenSlot)).isEqualTo("OPEN");
            assertThat(jdbc.queryForObject("SELECT locked_mountain_id FROM scheduling_day WHERE coach_id=? AND local_date=?",
                    String.class, coach, LocalDate.parse(date))).isNull();
        } finally {
            jdbc.execute("ALTER TABLE bookings_mail_task DROP CHECK ck_rejection_failure_test");
        }
        confirmBooking(coach, chosen);
        assertThat(rejectionMailCount(competitor)).isEqualTo(1);
        assertThat(rejectionMailCount(other)).isEqualTo(1);
    }

    private int rejectionMailCount(String booking) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=? AND event_type='BOOKING_REJECTED'",
                Integer.class, booking);
    }

    private void rejectBooking(String coach, String booking, String reason) throws Exception {
        mvc.perform(post("/api/coach/bookings/{id}/reject", booking).with(user(coach).roles("COACH")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    private void confirmBooking(String coach, String booking) throws Exception {
        mvc.perform(post("/api/coach/bookings/{id}/confirm", booking).with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    private String createMountain(String coach) throws Exception {
        var response = mvc.perform(post("/api/coach/mountains").with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other mountain " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(response.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void bookingMailTasksAreCreatedOnceAndDeepLinksRequireTheCorrectAccount() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        String other = account("STUDENT", "NOVICE");
        String course = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(18).toString();
        String slot = publishSlot(coach, course, date, "10:00");
        String key = UUID.randomUUID().toString();
        String body = application(course, slot, ensureMountain(coach));
        var response = mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String booking = JsonPath.read(response.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/bookings/{id}", booking).with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(booking))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/coach/bookings/{id}", booking).with(user(coach).roles("COACH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(booking));
        mvc.perform(get("/api/bookings/{id}", booking)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bookings/{id}", booking).with(user(other).roles("STUDENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings/{id}", booking).with(user(coach).roles("COACH")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/coach/bookings/{id}", booking).with(user(student).roles("STUDENT")))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=? AND event_type='APPLICATION_RECEIVED'",
                Integer.class, booking)).isEqualTo(1);
        mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=? AND event_type IN ('APPLICATION_RECEIVED','BOOKING_CONFIRMED')",
                Integer.class, booking)).isEqualTo(1);
        mvc.perform(post("/api/coach/bookings/{id}/confirm", booking)
                        .with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=? AND event_type IN ('APPLICATION_RECEIVED','BOOKING_CONFIRMED')",
                Integer.class, booking)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT recipient_account_id FROM bookings_mail_task WHERE booking_id=? AND event_type='BOOKING_CONFIRMED'",
                String.class, booking)).isEqualTo(student);
        mvc.perform(post("/api/coach/bookings/{id}/confirm", booking)
                        .with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=? AND event_type IN ('APPLICATION_RECEIVED','BOOKING_CONFIRMED')",
                Integer.class, booking)).isEqualTo(2);
    }

    @Test
    void failedMailTaskInsertRollsBackTheApplication() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        String course = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(20).toString();
        String slot = publishSlot(coach, course, date, "10:00");
        jdbc.update("DELETE FROM bookings_mail_task WHERE recipient_account_id=?", coach);
        jdbc.execute("ALTER TABLE bookings_mail_task ADD CONSTRAINT ck_mail_failure_test CHECK (recipient_account_id <> '"
                + coach + "')");
        try {
            mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(application(course, slot, ensureMountain(coach))))
                    .andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM bookings_request WHERE slot_id=? AND student_id=?",
                    Integer.class, slot, student)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE bookings_mail_task DROP CHECK ck_mail_failure_test");
        }
    }

    @Test
    void realSessionsCompleteCoachStudentCoachStudentBookingFlow() throws Exception {
        String coachId = account("COACH", null);
        String studentId = account("STUDENT", "BEGINNER");
        String password = "session booking test password";
        jdbc.update("UPDATE identity_account SET password_hash=? WHERE id IN (?,?)",
                passwords.encode(password), coachId, studentId);
        Cookie coach = login(coachId, password);
        Cookie student = login(studentId, password);
        mvc.perform(get("/api/auth/me").cookie(coach)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COACH"));
        mvc.perform(get("/api/auth/me").cookie(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"));
        String coachCsrf = csrfToken(coach);
        String studentCsrf = csrfToken(student);
        var course = mvc.perform(post("/api/coach/courses").cookie(coach).header("X-CSRF-TOKEN", coachCsrf)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Session 课程\",\"description\":\"\",\"priceAmount\":\"150.00\",\"currency\":\"CAD\"}"))
                .andExpect(status().isCreated()).andReturn();
        String courseId = JsonPath.read(course.getResponse().getContentAsString(), "$.id");
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(23).toString();
        var mountain = mvc.perform(post("/api/coach/mountains").cookie(coach).header("X-CSRF-TOKEN", coachCsrf)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Session Mountain\"}"))
                .andExpect(status().isCreated()).andReturn();
        String mountainId = JsonPath.read(mountain.getResponse().getContentAsString(), "$.id");
        var slot = mvc.perform(post("/api/coach/availability/batches").cookie(coach).header("X-CSRF-TOKEN", coachCsrf)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"days":[{"localDate":"%s","startTime":"10:00","endTime":"12:00"}]}
                                """.formatted(date)))
                .andExpect(status().isCreated()).andReturn();
        String slotId = JsonPath.read(slot.getResponse().getContentAsString(), "$.slots[0].id");
        mvc.perform(get("/api/courses").cookie(student)).andExpect(status().isOk());
        mvc.perform(get("/api/slots").cookie(student)
                        .param("from", date).param("to", date))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(slotId));
        var application = mvc.perform(post("/api/bookings").cookie(student).header("X-CSRF-TOKEN", studentCsrf)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(application(courseId, slotId, mountainId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        String bookingId = JsonPath.read(application.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/coach/bookings").cookie(coach)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(bookingId));
        mvc.perform(post("/api/coach/bookings/{id}/confirm", bookingId).cookie(coach)
                        .header("X-CSRF-TOKEN", coachCsrf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(get("/api/bookings/mine").cookie(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("CONFIRMED"));
        mvc.perform(post("/api/coach/courses").cookie(student).header("X-CSRF-TOKEN", studentCsrf)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void coachPublishesAndConfirmsOneOfSeveralPendingStudents() throws Exception {
        String coach = account("COACH", null);
        String alice = account("STUDENT", "BEGINNER");
        String bob = account("STUDENT", "NOVICE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_account", Integer.class)).isGreaterThanOrEqualTo(3);

        var createCourse = mvc.perform(post("/api/coach/courses").with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"单板基础课","description":"两小时一对一教学","priceAmount":"150.00","currency":"CAD"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("单板基础课"))
                .andReturn();
        String courseId = JsonPath.read(createCourse.getResponse().getContentAsString(), "$.id");
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(7).toString();
        String slotId = publishSlot(coach, courseId, date, "10:00");
        var listedCourses = mvc.perform(get("/api/courses").with(user(alice).roles("STUDENT")))
                .andExpect(status().isOk()).andReturn();
        assertThat((List<String>) JsonPath.read(listedCourses.getResponse().getContentAsString(), "$.items[*].id"))
                .contains(courseId);
        mvc.perform(get("/api/slots").with(user(alice).roles("STUDENT"))
                        .param("from", date).param("to", date))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(slotId));

        String firstId = apply(alice, slotId);
        String secondId = apply(bob, slotId);
        mvc.perform(post("/api/coach/bookings/{id}/confirm", firstId)
                        .with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(get("/api/bookings/mine").with(user(bob).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(secondId))
                .andExpect(jsonPath("$.items[0].status").value("REJECTED"));
        mvc.perform(post("/api/coach/bookings/{id}/confirm", secondId)
                        .with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/bookings/mine").with(user(coach).roles("COACH")))
                .andExpect(status().isForbidden());
    }

    @Test
    void idempotentApplicationAndRoleBoundaries() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        String courseId = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(9).toString();
        String slotId = publishSlot(coach, courseId, date, "10:00");
        String key = UUID.randomUUID().toString();
        String body = application(courseId, slotId, ensureMountain(coach));
        var first = mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String bookingId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(bookingId));
        mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(application(courseId, UUID.randomUUID().toString(), ensureMountain(coach))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/courses").with(user(coach).roles("COACH"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/coach/bookings/{id}/confirm", bookingId)
                        .with(user(student).roles("STUDENT")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsOverlappingAndDstGapSlotsButAllowsAdjacentSlots() throws Exception {
        String coach = account("COACH", null);
        String courseId = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(11).toString();
        publishSlot(coach, courseId, date, "10:00");
        mvc.perform(slotRequest(coach, courseId, date, "11:00")).andExpect(status().isConflict());
        mvc.perform(slotRequest(coach, courseId, date, "12:00")).andExpect(status().isCreated());
        var zone = ZoneId.of("America/Toronto");
        ZoneOffsetTransition transition = zone.getRules().nextTransition(Instant.now());
        while (!transition.isGap()) transition = zone.getRules().nextTransition(transition.getInstant());
        LocalDateTime nonexistent = transition.getDateTimeBefore().plusMinutes(30);
        mvc.perform(slotRequest(coach, courseId, nonexistent.toLocalDate().toString(),
                nonexistent.toLocalTime().toString())).andExpect(status().isBadRequest());
        while (!transition.isOverlap()) transition = zone.getRules().nextTransition(transition.getInstant());
        LocalDateTime ambiguous = transition.getDateTimeAfter().plusMinutes(30);
        mvc.perform(slotRequest(coach, courseId, ambiguous.toLocalDate().toString(),
                ambiguous.toLocalTime().toString())).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentCoachConfirmationsHaveExactlyOneWinner() throws Exception {
        String coach = account("COACH", null);
        String courseId = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(13).toString();
        String slotId = publishSlot(coach, courseId, date, "10:00");
        String first = apply(account("STUDENT", "BEGINNER"), slotId);
        String second = apply(account("STUDENT", "NOVICE"), slotId);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/coach/bookings/{id}/confirm", first)
                        .with(user(coach).roles("COACH")).with(csrf())).andReturn().getResponse().getStatus();
            });
            var two = pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/coach/bookings/{id}/confirm", second)
                        .with(user(coach).roles("COACH")).with(csrf())).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE slot_id=? AND status='CONFIRMED'",
                Integer.class, slotId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE slot_id=? AND status='REJECTED'",
                Integer.class, slotId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task t JOIN bookings_request b ON b.id=t.booking_id "
                        + "WHERE b.slot_id=? AND t.event_type='BOOKING_REJECTED'", Integer.class, slotId)).isEqualTo(1);
    }

    @Test
    void rejectedApplicationLeavesSlotOpenForAnotherStudent() throws Exception {
        String coach = account("COACH", null);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(15).toString();
        String slotId = publishSlot(coach, publishCourse(coach), date, "10:00");
        String rejected = apply(account("STUDENT", "BEGINNER"), slotId);
        mvc.perform(post("/api/coach/bookings/{id}/reject", rejected)
                        .with(user(coach).roles("COACH")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"时间不合适\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, slotId))
                .isEqualTo("OPEN");
        apply(account("STUDENT", "NOVICE"), slotId);
    }

    @Test
    void coursesAndApplicationsHaveBoundedCursorPages() throws Exception {
        String coach = account("COACH", null);
        String firstCourse = publishCourse(coach);
        String secondCourse = publishCourse(coach);
        String student = account("STUDENT", "BEGINNER");
        var firstPage = mvc.perform(get("/api/courses").with(user(student).roles("STUDENT"))
                        .param("limit", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(firstPage, "$.nextCursor");
        assertThat(cursor).isNotBlank();
        mvc.perform(get("/api/courses").with(user(student).roles("STUDENT"))
                        .param("limit", "1").param("cursor", cursor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/courses").with(user(student).roles("STUDENT")).param("limit", "51"))
                .andExpect(status().isBadRequest());
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(17).toString();
        apply(student, publishSlot(coach, firstCourse, date, "10:00"));
        apply(student, publishSlot(coach, secondCourse, date, "12:00"));
        var bookingPage = mvc.perform(get("/api/bookings/mine").with(user(student).roles("STUDENT"))
                        .param("limit", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bookingCursor = JsonPath.read(bookingPage, "$.nextCursor");
        mvc.perform(get("/api/bookings/mine").with(user(student).roles("STUDENT"))
                        .param("limit", "1").param("cursor", bookingCursor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void concurrentOverlappingSlotCreationPublishesOneSlot() throws Exception {
        String coach = account("COACH", null);
        String course = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(19).toString();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> {
                start.await();
                return mvc.perform(slotRequest(coach, course, date, "10:00"))
                        .andReturn().getResponse().getStatus();
            });
            var two = pool.submit(() -> {
                start.await();
                return mvc.perform(slotRequest(coach, course, date, "11:00"))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scheduling_slot WHERE local_date=? AND coach_id=?",
                Integer.class, date, coach)).isEqualTo(1);
    }

    @Test
    void applicationRacingWithConfirmationCannotRemainPending() throws Exception {
        String coach = account("COACH", null);
        String course = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(21).toString();
        String slotId = publishSlot(coach, course, date, "10:00");
        String firstBooking = apply(account("STUDENT", "BEGINNER"), slotId);
        String secondStudent = account("STUDENT", "NOVICE");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var application = pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/bookings").with(user(secondStudent).roles("STUDENT"))
                        .with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application(course, slotId, ensureMountain(coach))))
                        .andReturn().getResponse().getStatus();
            });
            var confirmation = pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/coach/bookings/{id}/confirm", firstBooking)
                        .with(user(coach).roles("COACH")).with(csrf()))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(confirmation.get()).isEqualTo(200);
            assertThat(application.get()).isIn(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE slot_id=? AND status='PENDING'",
                Integer.class, slotId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE slot_id=? AND status='CONFIRMED'",
                Integer.class, slotId)).isEqualTo(1);
    }

    @Test
    void revision4CourseEditAndArchivePreserveExistingApplication() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        String other = account("STUDENT", "BEGINNER");
        String course = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(25).toString();
        String slot = publishSlot(coach, course, date, "10:00");
        String application = apply(student, slot);
        String oldTitle = jdbc.queryForObject("SELECT course_title_snapshot FROM bookings_request WHERE id=?", String.class, application);
        mvc.perform(patch("/api/coach/courses/{id}", course).with(user(coach).roles("COACH")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"进阶课\",\"description\":\"新版介绍\",\"priceAmount\":\"220.00\",\"currency\":\"CAD\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("进阶课"));
        assertThat(jdbc.queryForObject("SELECT course_title_snapshot FROM bookings_request WHERE id=?", String.class, application))
                .isEqualTo(oldTitle);
        mvc.perform(post("/api/coach/courses/{id}/archive", course).with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        String publicCourses = mvc.perform(get("/api/courses").with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(publicCourses, "$.items[*].id")).doesNotContain(course);
        mvc.perform(post("/api/bookings").with(user(other).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application(course, slot, ensureMountain(coach))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/coach/bookings/{id}/confirm", application)
                        .with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void revision4RequiresRejectReasonAndAllowsCancelThenReapply() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        String course = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(26).toString();
        String slot = publishSlot(coach, course, date, "10:00");
        String first = apply(student, slot);
        mvc.perform(post("/api/coach/bookings/{id}/reject", first)
                        .with(user(coach).roles("COACH")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM bookings_request WHERE id=?", String.class, first))
                .isEqualTo("PENDING");
        mvc.perform(post("/api/bookings/{id}/cancel", first).with(user(student).roles("STUDENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED_BY_STUDENT"));
        mvc.perform(post("/api/bookings/{id}/cancel", first).with(user(student).roles("STUDENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        String originalKey = jdbc.queryForObject("SELECT idempotency_key FROM bookings_request WHERE id=?",
                String.class, first);
        mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", originalKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application(course, slot, ensureMountain(coach))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_STUDENT"));
        mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application(course, slot, ensureMountain(coach))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void revision4ConfirmedCancellationReopensSlotAndUnlocksLastMountain() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        String other = account("STUDENT", "BEGINNER");
        String course = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(27).toString();
        String slot = publishSlot(coach, course, date, "10:00");
        String booking = apply(student, slot);
        mvc.perform(post("/api/coach/bookings/{id}/confirm", booking)
                        .with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/bookings/{id}/cancel", booking).with(user(student).roles("STUDENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"行程变更\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED_BY_STUDENT"));
        assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, slot))
                .isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT locked_mountain_id FROM scheduling_day WHERE coach_id=? AND local_date=?",
                String.class, coach, LocalDate.parse(date))).isNull();
        mvc.perform(post("/api/bookings/{id}/cancel", booking).with(user(other).roles("STUDENT"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void revision4KeepsMountainLockUntilTheLastConfirmedBookingIsCancelled() throws Exception {
        String coach = account("COACH", null);
        String firstStudent = account("STUDENT", "BEGINNER");
        String secondStudent = account("STUDENT", "BEGINNER");
        String course = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(28).toString();
        String firstSlot = publishSlot(coach, course, date, "10:00");
        String secondSlot = publishSlot(coach, course, date, "12:00");
        String firstBooking = apply(firstStudent, firstSlot);
        String secondBooking = apply(secondStudent, secondSlot);
        mvc.perform(post("/api/coach/bookings/{id}/confirm", firstBooking)
                        .with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/coach/bookings/{id}/confirm", secondBooking)
                        .with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isOk());
        String locked = jdbc.queryForObject("SELECT locked_mountain_id FROM scheduling_day WHERE coach_id=? AND local_date=?",
                String.class, coach, LocalDate.parse(date));
        assertThat(locked).isNotNull();
        mvc.perform(post("/api/bookings/{id}/cancel", firstBooking).with(user(firstStudent).roles("STUDENT"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT locked_mountain_id FROM scheduling_day WHERE coach_id=? AND local_date=?",
                String.class, coach, LocalDate.parse(date))).isEqualTo(locked);
        mvc.perform(post("/api/bookings/{id}/cancel", secondBooking).with(user(secondStudent).roles("STUDENT"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT locked_mountain_id FROM scheduling_day WHERE coach_id=? AND local_date=?",
                String.class, coach, LocalDate.parse(date))).isNull();
    }

    @Test
    void revision4RefusesConfirmedCancellationWithinTwentyFourHours() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        ZoneId toronto = ZoneId.of("America/Toronto");
        boolean beforeTen = LocalTime.now(toronto).isBefore(LocalTime.of(10, 0));
        String date = LocalDate.now(toronto).plusDays(beforeTen ? 0 : 1).toString();
        String slot = publishSlot(coach, publishCourse(coach), date, beforeTen ? "12:00" : "09:00");
        String booking = apply(student, slot);
        mvc.perform(post("/api/coach/bookings/{id}/confirm", booking)
                        .with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/bookings/{id}/cancel", booking).with(user(student).roles("STUDENT"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, slot))
                .isEqualTo("BOOKED");
    }

    @Test
    void revision4ConcurrentConfirmAndCancelLeaveNoBookedSlotBehind() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(29).toString();
        String slot = publishSlot(coach, publishCourse(coach), date, "10:00");
        String booking = apply(student, slot);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var confirm = executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/coach/bookings/{id}/confirm", booking)
                        .with(user(coach).roles("COACH")).with(csrf())).andReturn().getResponse().getStatus();
            });
            var cancel = executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/bookings/{id}/cancel", booking)
                        .with(user(student).roles("STUDENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(cancel.get()).isEqualTo(200);
            assertThat(confirm.get()).isIn(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM bookings_request WHERE id=?", String.class, booking))
                .isEqualTo("CANCELLED_BY_STUDENT");
        assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, slot))
                .isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT locked_mountain_id FROM scheduling_day WHERE coach_id=? AND local_date=?",
                String.class, coach, LocalDate.parse(date))).isNull();
    }

    @Test
    void revision4ConcurrentCourseArchiveAndApplicationHaveOneClearOrder() throws Exception {
        String coach = account("COACH", null);
        String student = account("STUDENT", "BEGINNER");
        String course = publishCourse(coach);
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(30).toString();
        String slot = publishSlot(coach, course, date, "10:00");
        String mountain = ensureMountain(coach);
        CountDownLatch start = new CountDownLatch(1);
        int applyStatus;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var archive = executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/coach/courses/{id}/archive", course)
                        .with(user(coach).roles("COACH")).with(csrf())).andReturn().getResponse().getStatus();
            });
            var apply = executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(application(course, slot, mountain)))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(archive.get()).isEqualTo(200);
            applyStatus = apply.get();
        }
        assertThat(applyStatus).isIn(201, 409);
        assertThat(jdbc.queryForObject("SELECT active FROM catalog_course WHERE id=?", Boolean.class, course))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE student_id=? AND slot_id=?",
                Integer.class, student, slot)).isEqualTo(applyStatus == 201 ? 1 : 0);
    }

    private String publishCourse(String coach) throws Exception {
        var response = mvc.perform(post("/api/coach/courses").with(user(coach).roles("COACH")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"基础课\",\"description\":\"\",\"priceAmount\":\"150.00\",\"currency\":\"CAD\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(response.getResponse().getContentAsString(), "$.id");
    }
    private Cookie login(String id, String password) throws Exception {
        String email = jdbc.queryForObject("SELECT email FROM identity_account WHERE id=?", String.class, id);
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }
    private String csrfToken(Cookie cookie) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(response.getResponse().getContentAsString(), "$.token");
    }
    private String publishSlot(String coach, String courseId, String date, String time) throws Exception {
        var response = mvc.perform(slotRequest(coach, courseId, date, time))
                .andExpect(status().isCreated()).andReturn();
        String slotId = JsonPath.read(response.getResponse().getContentAsString(), "$.slots[0].id");
        slotCourses.put(slotId, courseId);
        return slotId;
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder slotRequest(
            String coach, String courseId, String date, String time) {
        ensureMountain(coach);
        String end = LocalTime.parse(time).plusHours(2).toString();
        return post("/api/coach/availability/batches").with(user(coach).roles("COACH")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"days":[{"localDate":"%s","startTime":"%s","endTime":"%s"}]}
                        """.formatted(date, time, end));
    }

    private String apply(String student, String slotId) throws Exception {
        String coachId = jdbc.queryForObject("SELECT coach_id FROM scheduling_slot WHERE id=?", String.class, slotId);
        String mountainId = ensureMountain(coachId);
        return apply(student, slotId, mountainId);
    }
    private String apply(String student, String slotId, String mountainId) throws Exception {
        String courseId = slotCourses.get(slotId);
        var result = mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(application(courseId, slotId, mountainId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private synchronized String ensureMountain(String coach) {
        String existing = coachMountains.get(coach);
        if (existing != null) return existing;
        try {
            var result = mvc.perform(post("/api/coach/mountains").with(user(coach).roles("COACH")).with(csrf())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Blue Mountain " + UUID.randomUUID() + "\"}"))
                    .andExpect(status().isCreated()).andReturn();
            String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
            coachMountains.put(coach, id);
            return id;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static String application(String courseId, String slotId, String mountainId) {
        return "{\"courseId\":\"" + courseId + "\",\"slotId\":\"" + slotId
                + "\",\"mountainId\":\"" + mountainId + "\"}";
    }

    private String account(String role, String level) {
        if (role.equals("COACH")) {
            String existing = jdbc.query("SELECT id FROM identity_account WHERE role='COACH'",
                    rs -> rs.next() ? rs.getString(1) : null);
            if (existing != null) return existing;
        }
        String id = UUID.randomUUID().toString();
        String email = id + "@example.test";
        jdbc.update("""
                INSERT INTO identity_account (id,email,email_key,name,level,role,password_hash,verified_at,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, email, email, role.equals("COACH") ? "教练" : "学员", level,
                role, "test-hash", Instant.now(), Instant.now());
        if (role.equals("STUDENT"))
            jdbc.update("UPDATE identity_account SET contact_phone=? WHERE id=?", "+14165550123", id);
        return id;
    }
}
