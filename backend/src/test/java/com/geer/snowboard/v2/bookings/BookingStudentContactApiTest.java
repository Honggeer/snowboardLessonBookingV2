package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations;
import com.geer.snowboard.v2.identity.application.port.out.PasswordHashes;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {"identity.mail.worker.enabled=false", "booking.mail.worker.enabled=false", "spring.session.jdbc.cleanup-cron=-"})
@AutoConfigureMockMvc
@Testcontainers
class BookingStudentContactApiTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("student_contact_test").withUsername("contact_test").withPassword("test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHashes passwords;
    @Autowired BookingOperations bookings;

    @Test void missingPhoneRejectsNewBookingBeforeWritesIncludingDirectUseCase() throws Exception {
        var f = fixture();
        int mailBefore = jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE recipient_account_id=?", Integer.class, f.coach());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE student_id=?", Integer.class, f.student())).isZero();
        mvc.perform(application(f, UUID.randomUUID().toString())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("请先填写联系电话，用于教练联系并确认预约"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookings.apply(new Actor(f.student(), "STUDENT", "学员"),
                new BookingOperations.Apply(f.course(), f.slot(), f.mountain()), UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(BusinessProblem.class, p -> assertThat(p.status()).isEqualTo(400));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE student_id=?", Integer.class, f.student())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE recipient_account_id=?", Integer.class, f.coach())).isEqualTo(mailBefore);
    }

    @Test void savedPhoneAllowsBookingAndCoachReadsCurrentContactWithoutStudentLeakage() throws Exception {
        var f = fixture(); save(f.student(), "+1 (416) 555-0123", "+14165550123");
        String key = UUID.randomUUID().toString();
        String id = booking(f, key);
        mvc.perform(get("/api/coach/bookings/{id}", id).with(user(f.coach()).roles("COACH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentPhone").value("+14165550123"));
        String cursor = null;
        Set<String> seen = new HashSet<>();
        do {
            var request = get("/api/coach/bookings").param("limit", "1").with(user(f.coach()).roles("COACH"));
            if (cursor != null) request.param("cursor", cursor);
            String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> items = JsonPath.read(body, "$.items");
            for (var item : items) {
                assertThat(seen.add((String) item.get("id"))).isTrue();
                if (id.equals(item.get("id"))) assertThat(item.get("studentPhone")).isEqualTo("+14165550123");
            }
            cursor = JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);
        assertThat(seen).contains(id);
        save(f.student(), "+86 138 0013 8000", "+8613800138000");
        mvc.perform(get("/api/coach/bookings/{id}", id).with(user(f.coach()).roles("COACH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentPhone").value("+8613800138000"));
        mvc.perform(application(f, key)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.studentPhone").doesNotExist());
        mvc.perform(get("/api/bookings/{id}", id).with(user(f.student()).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentPhone").doesNotExist());
        mvc.perform(get("/api/bookings/mine").with(user(f.student()).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].studentPhone").doesNotExist());
        String other = account("STUDENT");
        mvc.perform(get("/api/bookings/{id}", id).with(user(other).roles("STUDENT"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/coach/bookings/{id}", id).with(user(other).roles("STUDENT"))).andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookings.coachOne(new Actor(other, "COACH", "错误教练"), id))
                .isInstanceOfSatisfying(BusinessProblem.class, p -> assertThat(p.status()).isEqualTo(404));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test void oldBookingWithoutPhoneCanReplayConfirmCancelAndReject() throws Exception {
        var f = fixture(); save(f.student(), "+14165550123", "+14165550123");
        String key = UUID.randomUUID().toString(), id = booking(f, key);
        jdbc.update("UPDATE identity_account SET contact_phone=NULL WHERE id=?", f.student());
        mvc.perform(get("/api/coach/bookings/{id}", id).with(user(f.coach()).roles("COACH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studentPhone").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(application(f, key)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post("/api/coach/bookings/{id}/confirm", id).with(user(f.coach()).roles("COACH")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/api/bookings/{id}/cancel", id).with(user(f.student()).roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"时间变化\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED_BY_STUDENT"));
        var second = fixture(); save(second.student(), "+14165550124", "+14165550124");
        String rejected = booking(second, UUID.randomUUID().toString());
        jdbc.update("UPDATE identity_account SET contact_phone=NULL WHERE id=?", second.student());
        mvc.perform(post("/api/coach/bookings/{id}/reject", rejected).with(user(second.coach()).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"教练时间变化\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test void contactsAreSelfOnlyRequireCsrfAndDoNotChangeCredentials() throws Exception {
        String student = account("STUDENT"), other = account("STUDENT"), coach = coach();
        mvc.perform(get("/api/student/contact").with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/student/contact")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/student/contact").with(user(coach).roles("COACH"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/student/contact").with(user(student).roles("STUDENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+14165550123\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/student/contact").with(user(coach).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+14165550123\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/student/contact").with(user(student).roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"phone":"+1 416 555 0123","studentId":"%s"}
                        """.formatted(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("+14165550123"));
        mvc.perform(get("/api/student/contact").with(user(other).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(org.hamcrest.Matchers.nullValue()));
        assertThat(jdbc.queryForObject("SELECT credential_version FROM identity_account WHERE id=?", Long.class, student)).isZero();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM identity_account WHERE id=?", String.class, student)).isEqualTo("test-hash");
    }

    @Test void invalidPhoneCannotClearExistingValueAndInputBoundariesAreEnforced() throws Exception {
        String student = account("STUDENT"); save(student, "+14165550123", "+14165550123");
        for (String phone : List.of("", "  ", "4165550123", "+01234567", "+123456", "+1234567890123456", "+1abc5550123", "+1/4165550123", " ".repeat(49) + "+123456789012345")) {
            mvc.perform(patch("/api/student/contact").with(user(student).roles("STUDENT")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + phone + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/student/contact").with(user(student).roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/student/contact").with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("+14165550123"));
        save(student, "+1234567", "+1234567"); save(student, " ".repeat(48) + "+123456789012345", "+123456789012345");
    }

    @Test void realSessionWithoutPhoneStillLogsInAndContactPersistsAcrossLogins() throws Exception {
        String student = account("STUDENT"), password = "private-test-password";
        jdbc.update("UPDATE identity_account SET password_hash=? WHERE id=?", passwords.encode(password), student);
        Cookie cookie = login(student, password);
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());
        mvc.perform(get("/api/courses").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/student/contact").cookie(cookie)).andExpect(status().isOk());
        String token = JsonPath.read(mvc.perform(get("/api/auth/csrf").cookie(cookie)).andReturn().getResponse().getContentAsString(), "$.token");
        mvc.perform(patch("/api/student/contact").cookie(cookie).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+14165550123\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").cookie(cookie).header("X-CSRF-TOKEN", token)).andExpect(status().isOk());
        mvc.perform(get("/api/student/contact").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/student/contact").cookie(login(student, password)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("+14165550123"));
    }

    @Test void concurrentPhoneChangesAndBookingRetriesStayAtomicAndCreateOneApplication() throws Exception {
        var f = fixture(); save(f.student(), "+14165550123", "+14165550123");
        String key = UUID.randomUUID().toString();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var firstSave = pool.submit(() -> { start.await(); save(f.student(), "+14165550124", "+14165550124"); return true; });
            var secondSave = pool.submit(() -> { start.await(); save(f.student(), "+8613800138000", "+8613800138000"); return true; });
            var firstApply = pool.submit(() -> { start.await(); return mvc.perform(application(f, key)).andReturn().getResponse(); });
            var secondApply = pool.submit(() -> { start.await(); return mvc.perform(application(f, key)).andReturn().getResponse(); });
            start.countDown();
            assertThat(firstSave.get(20, TimeUnit.SECONDS)).isTrue(); assertThat(secondSave.get(20, TimeUnit.SECONDS)).isTrue();
            var first = firstApply.get(20, TimeUnit.SECONDS); var second = secondApply.get(20, TimeUnit.SECONDS);
            assertThat(List.of(first.getStatus(), second.getStatus())).containsExactlyInAnyOrder(201, 200);
            assertThat((String) JsonPath.read(first.getContentAsString(), "$.id")).isEqualTo(JsonPath.read(second.getContentAsString(), "$.id"));
            String id = JsonPath.read(first.getContentAsString(), "$.id");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_request WHERE student_id=?", Integer.class, f.student())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=?", Integer.class, id)).isEqualTo(1);
        }
        String phone = jdbc.queryForObject("SELECT contact_phone FROM identity_account WHERE id=?", String.class, f.student());
        assertThat(phone).isIn("+14165550124", "+8613800138000");
    }

    private Cookie login(String id, String password) throws Exception {
        String email = jdbc.queryForObject("SELECT email FROM identity_account WHERE id=?", String.class, id);
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }
    private void save(String student, String value, String normalized) throws Exception {
        mvc.perform(patch("/api/student/contact").with(user(student).roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"" + value + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value(normalized));
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
        String date = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(days).toString();
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
