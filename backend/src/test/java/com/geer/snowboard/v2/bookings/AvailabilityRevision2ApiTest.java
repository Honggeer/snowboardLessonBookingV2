package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
class AvailabilityRevision2ApiTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("availability_r2_test").withUsername("availability_r2_test")
            .withPassword("test_password");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void coachCanPublishTwoHourAvailabilityWithoutCreatingAnyCourse() throws Exception {
        String coach = account("COACH");
        String mountain = mountain(coach, "Blue Mountain");
        String date = date(6);
        String next = date(7);
        var result = mvc.perform(post("/api/coach/availability/batches")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"days":[{"localDate":"%s","startTime":"09:00","endTime":"14:30"},
                                         {"localDate":"%s","startTime":"10:00","endTime":"12:00","mountainId":"%s"}]}
                                """.formatted(date, next, mountain)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.tails[0].startTime").value("13:00"))
                .andReturn();
        String slot = JsonPath.read(result.getResponse().getContentAsString(), "$.slots[0].id");
        assertThat(jdbc.queryForObject("SELECT course_id FROM scheduling_slot WHERE id=?", String.class, slot)).isNull();
        String student = account("STUDENT");
        String open = mvc.perform(get("/api/slots").with(user(student).roles("STUDENT"))
                        .param("from", date).param("to", next))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> mountainIds = JsonPath.read(open, "$.items[*].availableMountains[*].id");
        assertThat(mountainIds).contains(mountain);
    }

    @Test
    void firstConfirmationLocksTheMountainForTheWholeTorontoDay() throws Exception {
        String coach = account("COACH");
        String blue = mountain(coach, "Blue Mountain " + UUID.randomUUID());
        String horseshoe = mountain(coach, "Horseshoe " + UUID.randomUUID());
        String course = course(coach);
        String anotherCourse = course(coach, "220.00");
        String date = date(8);
        String[] slots = batch(coach, date, "10:00", "14:00");
        String alice = account("STUDENT");
        String bob = account("STUDENT");
        String chosen = apply(alice, course, slots[0], blue);
        String otherMountain = apply(bob, course, slots[1], horseshoe);
        String sameSlotDifferentCourse = apply(account("STUDENT"), anotherCourse, slots[0], blue);
        assertThat(jdbc.queryForObject("SELECT course_id FROM bookings_request WHERE id=?",
                String.class, sameSlotDifferentCourse)).isEqualTo(anotherCourse);
        assertThat(jdbc.queryForObject("SELECT price_amount_snapshot FROM bookings_request WHERE id=?",
                java.math.BigDecimal.class, sameSlotDifferentCourse)).isEqualByComparingTo("220.00");
        mvc.perform(post("/api/coach/bookings/{id}/confirm", chosen)
                        .with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(get("/api/bookings/mine").with(user(bob).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(otherMountain))
                .andExpect(jsonPath("$.items[0].status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT status FROM bookings_request WHERE id=?",
                String.class, sameSlotDifferentCourse)).isEqualTo("REJECTED");
        mvc.perform(post("/api/bookings").with(user(account("STUDENT")).roles("STUDENT"))
                        .with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application(course, slots[1], horseshoe)))
                .andExpect(status().isConflict());
        String sameMountain = apply(account("STUDENT"), course, slots[1], blue);
        mvc.perform(post("/api/coach/bookings/{id}/confirm", sameMountain)
                        .with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void mountainRenamePreservesBookingSnapshotAndPendingBlocksDeactivation() throws Exception {
        String coach = account("COACH");
        String mountain = mountain(coach, "Rename me " + UUID.randomUUID());
        String course = course(coach);
        String slot = batch(coach, date(10), "10:00", "12:00")[0];
        String booking = apply(account("STUDENT"), course, slot, mountain);
        String original = jdbc.queryForObject("SELECT location_snapshot FROM bookings_request WHERE id=?",
                String.class, booking);
        mvc.perform(patch("/api/coach/mountains/{id}", mountain)
                        .with(user(coach).roles("COACH")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New mountain name\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New mountain name"));
        mvc.perform(post("/api/coach/mountains/{id}/deactivate", mountain)
                        .with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT location_snapshot FROM bookings_request WHERE id=?",
                String.class, booking)).isEqualTo(original);
        mvc.perform(post("/api/coach/bookings/{id}/reject", booking)
                        .with(user(coach).roles("COACH")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"行程冲突\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/coach/mountains/{id}/deactivate", mountain)
                        .with(user(coach).roles("COACH")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/bookings").with(user(account("STUDENT")).roles("STUDENT"))
                        .with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(application(course, slot, mountain)))
                .andExpect(status().isConflict());
    }

    @Test
    void batchConflictRollsBackEveryDayAndIdempotencyReturnsTheOriginalSlots() throws Exception {
        String coach = account("COACH");
        mountain(coach, "Batch mountain " + UUID.randomUUID());
        String existingDay = date(12);
        String newDay = date(13);
        String key = UUID.randomUUID().toString();
        String body = "{\"days\":[{\"localDate\":\"" + existingDay
                + "\",\"startTime\":\"10:00\",\"endTime\":\"12:00\"}]}";
        String first = mvc.perform(post("/api/coach/availability/batches")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String originalSlot = JsonPath.read(first, "$.slots[0].id");
        mvc.perform(post("/api/coach/availability/batches")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots[0].id").value(originalSlot));
        mvc.perform(post("/api/coach/availability/batches")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("10:00", "09:00")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/coach/availability/batches")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"days":[{"localDate":"%s","startTime":"10:00","endTime":"12:00"},
                                         {"localDate":"%s","startTime":"10:00","endTime":"12:00"}]}
                                """.formatted(newDay, existingDay)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scheduling_slot WHERE coach_id=? AND local_date=?",
                Integer.class, coach, newDay)).isZero();
    }

    @Test
    void twoDifferentMountainConfirmationsOnTheSameDayHaveOneWinner() throws Exception {
        String coach = account("COACH");
        String firstMountain = mountain(coach, "Concurrent A " + UUID.randomUUID());
        String secondMountain = mountain(coach, "Concurrent B " + UUID.randomUUID());
        String course = course(coach);
        String date = date(16);
        String[] slots = batch(coach, date, "10:00", "14:00");
        String first = apply(account("STUDENT"), course, slots[0], firstMountain);
        String second = apply(account("STUDENT"), course, slots[1], secondMountain);
        CountDownLatch start = new CountDownLatch(1);
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE id IN (?,?) AND status='CONFIRMED'",
                Integer.class, first, second)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE id IN (?,?) AND status='REJECTED'",
                Integer.class, first, second)).isEqualTo(1);
        String confirmedMountain = jdbc.queryForObject("SELECT mountain_id FROM bookings_request WHERE status='CONFIRMED' AND id IN (?,?)",
                String.class, first, second);
        assertThat(jdbc.queryForObject("SELECT locked_mountain_id FROM scheduling_day WHERE coach_id=? AND local_date=?",
                String.class, coach, date)).isEqualTo(confirmedMountain);
    }

    private String mountain(String coach, String name) throws Exception {
        var result = mvc.perform(post("/api/coach/mountains").with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String course(String coach) throws Exception {
        return course(coach, "150.00");
    }

    private String course(String coach, String price) throws Exception {
        var result = mvc.perform(post("/api/coach/courses").with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"title":"单板课","description":"","priceAmount":"%s","currency":"CAD"}
                                """.formatted(price)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String[] batch(String coach, String date, String start, String end) throws Exception {
        var result = mvc.perform(post("/api/coach/availability/batches")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"days":[{"localDate":"%s","startTime":"%s","endTime":"%s"}]}
                                """.formatted(date, start, end)))
                .andExpect(status().isCreated()).andReturn();
        List<String> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.slots[*].id");
        return ids.toArray(String[]::new);
    }

    private String apply(String student, String course, String slot, String mountain) throws Exception {
        var result = mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(application(course, slot, mountain)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String application(String course, String slot, String mountain) {
        return "{\"courseId\":\"" + course + "\",\"slotId\":\"" + slot
                + "\",\"mountainId\":\"" + mountain + "\"}";
    }

    private static String date(int offset) {
        return LocalDate.now(ZoneId.of("America/Toronto")).plusDays(offset).toString();
    }

    private String account(String role) {
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
                """, id, email, email, role.equals("COACH") ? "教练" : "学员",
                role.equals("COACH") ? null : "BEGINNER", role, "test-hash", Instant.now(), Instant.now());
        return id;
    }
}
