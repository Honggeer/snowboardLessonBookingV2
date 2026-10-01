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
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneOffsetTransition;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        "identity.mail.worker.enabled=false", "spring.session.jdbc.cleanup-cron=-"})
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
    private final Map<String, String> slotCourses = new ConcurrentHashMap<>();
    private final Map<String, String> coachMountains = new ConcurrentHashMap<>();

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
        String courseId = slotCourses.get(slotId);
        String mountainId = ensureMountain(coachId);
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
        return id;
    }
}
