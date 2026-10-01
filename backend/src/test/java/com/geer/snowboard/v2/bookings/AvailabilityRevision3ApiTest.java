package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.List;
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
class AvailabilityRevision3ApiTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("availability_r3_test").withUsername("availability_r3_test")
            .withPassword("test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void monthReadAndWholeDayReplacementKeepOldRowsAndReplay() throws Exception {
        String coach = account("COACH");
        mountain(coach);
        String date = day(7);
        String old = publish(coach, date, "10:00", "12:00");
        String key = UUID.randomUUID().toString();
        String body = batchBody(date, "13:00", "17:00");
        mvc.perform(get("/api/coach/availability/month").with(user(coach).roles("COACH"))
                        .param("year", date.substring(0, 4)).param("month", String.valueOf(Integer.parseInt(date.substring(5, 7)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zoneId").value("America/Toronto"));
        String replacement = mvc.perform(post("/api/coach/availability/replacements")
                        .with(user(coach).roles("COACH")).with(csrf()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.slots.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String newSlot = JsonPath.read(replacement, "$.slots[0].id");
        assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, old))
                .isEqualTo("CLOSED");
        mvc.perform(post("/api/coach/availability/replacements")
                        .with(user(coach).roles("COACH")).with(csrf()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots[0].id").value(newSlot));
        mvc.perform(post("/api/coach/availability/replacements")
                        .with(user(coach).roles("COACH")).with(csrf()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(batchBody(date, "09:00", "11:00")))
                .andExpect(status().isConflict());
    }

    @Test void pendingApplicationBlocksTheEntireReplacementBatch() throws Exception {
        String coach = account("COACH");
        String mountain = mountain(coach);
        String date = day(8);
        String untouched = day(9);
        String slot = publish(coach, date, "10:00", "12:00");
        String course = course(coach);
        String student = account("STUDENT");
        mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"courseId":"%s","slotId":"%s","mountainId":"%s"}
                                """.formatted(course, slot, mountain))).andExpect(status().isCreated());
        mvc.perform(post("/api/coach/availability/replacements")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"days":[{"localDate":"%s","startTime":"13:00","endTime":"15:00"},
                                         {"localDate":"%s","startTime":"13:00","endTime":"15:00"}]}
                                """.formatted(date, untouched)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, slot))
                .isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scheduling_slot WHERE coach_id=? AND local_date=?",
                Integer.class, coach, untouched)).isZero();
    }

    @Test void confirmedSlotSurvivesReplacementAndLockedMountainAndOverlapAreEnforced() throws Exception {
        String coach = account("COACH");
        String blue = mountain(coach);
        String other = mountain(coach);
        String date = day(11);
        String booked = publish(coach, date, "10:00", "12:00");
        String open = publish(coach, date, "12:00", "14:00");
        String course = course(coach);
        String student = account("STUDENT");
        String application = mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"courseId":"%s","slotId":"%s","mountainId":"%s"}
                                """.formatted(course, booked, blue)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String bookingId = JsonPath.read(application, "$.id");
        mvc.perform(post("/api/coach/bookings/{id}/confirm", bookingId)
                        .with(user(coach).roles("COACH")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/coach/availability/replacements")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(date, "11:00", "15:00")))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, open))
                .isEqualTo("OPEN");
        mvc.perform(post("/api/coach/availability/replacements")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"days":[{"localDate":"%s","startTime":"12:00","endTime":"14:00","mountainId":"%s"}]}
                                """.formatted(date, other)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/coach/availability/replacements")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(date, "13:00", "17:00")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.slots.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, booked))
                .isEqualTo("BOOKED");
        assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, open))
                .isEqualTo("CLOSED");
        String month = mvc.perform(get("/api/coach/availability/month").with(user(coach).roles("COACH"))
                        .param("year", date.substring(0, 4))
                        .param("month", String.valueOf(Integer.parseInt(date.substring(5, 7)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((java.util.List<String>) JsonPath.read(month,
                "$.days[?(@.localDate=='" + date + "')].lockedMountain.id")).containsExactly(blue);
        java.util.List<java.util.List<Object>> daySlots = JsonPath.read(month,
                "$.days[?(@.localDate=='" + date + "')].slots");
        assertThat(daySlots).hasSize(1);
        assertThat(daySlots.getFirst()).hasSize(3);
        mvc.perform(get("/api/coach/availability/month").with(user(student).roles("STUDENT"))
                        .param("year", date.substring(0, 4)).param("month", date.substring(5, 7)))
                .andExpect(status().isForbidden());
    }

    @Test void concurrentApplicationAndReplacementNeverLeavePendingOnClosedSlot() throws Exception {
        String coach = account("COACH");
        String mountain = mountain(coach);
        String course = course(coach);
        String date = day(14);
        String slot = publish(coach, date, "10:00", "12:00");
        String student = account("STUDENT");
        CountDownLatch start = new CountDownLatch(1);
        int applyStatus;
        int replaceStatus;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var apply = pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"courseId":"%s","slotId":"%s","mountainId":"%s"}
                                """.formatted(course, slot, mountain))).andReturn().getResponse().getStatus();
            });
            var replace = pool.submit(() -> {
                start.await();
                return mvc.perform(post("/api/coach/availability/replacements")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(batchBody(date, "13:00", "15:00")))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            applyStatus = apply.get();
            replaceStatus = replace.get();
        }
        assertThat(List.of(applyStatus, replaceStatus)).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM bookings_request b JOIN scheduling_slot s ON s.id=b.slot_id
                WHERE b.status='PENDING' AND s.status='CLOSED' AND b.slot_id=?
                """, Integer.class, slot)).isZero();
    }

    private String publish(String coach, String date, String start, String end) throws Exception {
        var result = mvc.perform(post("/api/coach/availability/batches")
                        .with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(batchBody(date, start, end)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.slots[0].id");
    }
    private String mountain(String coach) throws Exception {
        var result = mvc.perform(post("/api/coach/mountains").with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"R3 mountain " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
    private String course(String coach) throws Exception {
        var result = mvc.perform(post("/api/coach/courses").with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"单板课\",\"description\":\"\",\"priceAmount\":\"150.00\",\"currency\":\"CAD\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
    private static String batchBody(String date, String start, String end) {
        return "{\"days\":[{\"localDate\":\"" + date + "\",\"startTime\":\"" + start
                + "\",\"endTime\":\"" + end + "\"}]}";
    }
    private static String day(int offset) {
        return LocalDate.now(ZoneId.of("America/Toronto")).plusDays(offset).toString();
    }
}
