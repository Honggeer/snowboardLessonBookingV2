package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, properties = {
        "identity.mail.worker.enabled=false", "booking.mail.worker.enabled=false", "spring.session.jdbc.cleanup-cron=-"})
@AutoConfigureMockMvc
@Testcontainers
@Import(AvailabilityRevision5ApiTest.TestClock.class)
class AvailabilityRevision5ApiTest {
    private static final Instant NOW = Instant.parse("2026-10-05T16:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @TestConfiguration static class TestClock {
        @Bean @Primary Clock availabilityTestClock() { return Clock.fixed(NOW, ZoneId.of("UTC")); }
    }

    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("availability_r5_test").withUsername("availability_r5_test")
            .withPassword("test_password");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clearTestScheduling() {
        // This class owns its disposable MySQL container; isolate each endpoint scenario.
        for (String table : List.of("bookings_mail_task", "bookings_request", "scheduling_slot",
                "scheduling_day", "scheduling_batch", "catalog_course", "scheduling_mountain")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"batches", "replacements"})
    void fartherFutureDatesPublishReplayAndRemainBookable(String endpoint) throws Exception {
        String coach = account("COACH");
        String mountain = mountain(coach);
        List<String> dates = List.of(day(32), day(90), day(370));
        String body = batch(dates, "10:00", "12:00", mountain);
        String key = UUID.randomUUID().toString();
        String result = publish(endpoint, coach, body, key).andExpect(status().isCreated())
                .andExpect(jsonPath("$.slots.length()").value(3)).andReturn().getResponse().getContentAsString();
        List<String> originalIds = JsonPath.read(result, "$.slots[*].id");
        publish(endpoint, coach, body, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].id").value(originalIds.getFirst()));
        publish(endpoint, coach, batch(dates, "13:00", "15:00", mountain), key)
                .andExpect(status().isConflict());
        assertThat(slotCount()).isEqualTo(3);

        if (endpoint.equals("replacements")) {
            result = publish(endpoint, coach, batch(dates, "13:00", "15:00", mountain), UUID.randomUUID().toString())
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            for (String id : originalIds) {
                assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, id))
                        .isEqualTo("CLOSED");
            }
        }
        List<String> activeIds = JsonPath.read(result, "$.slots[*].id");
        String student = account("STUDENT");
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = LocalDate.parse(dates.get(i));
            String month = mvc.perform(get("/api/coach/availability/month").with(user(coach).roles("COACH"))
                            .param("year", String.valueOf(date.getYear())).param("month", String.valueOf(date.getMonthValue())))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> monthIds = JsonPath.read(month, "$.days[*].slots[*].id");
            assertThat(monthIds).contains(activeIds.get(i));
            if (endpoint.equals("replacements")) assertThat(monthIds).doesNotContain(originalIds.get(i));
            mvc.perform(get("/api/slots").with(user(student).roles("STUDENT"))
                            .param("from", dates.get(i)).param("to", dates.get(i)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(activeIds.get(i)));
        }
        String course = course(coach);
        mvc.perform(post("/api/bookings").with(user(student).roles("STUDENT")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"%s\",\"slotId\":\"%s\",\"mountainId\":\"%s\"}"
                                .formatted(course, activeIds.get(1), mountain)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.localDate").value(dates.get(1)))
                .andExpect(jsonPath("$.courseTitle").value("远期单板课"));
        publish("replacements", coach, batch(dates, "17:00", "19:00", mountain), UUID.randomUUID().toString())
                .andExpect(status().isConflict());
        for (String id : activeIds) {
            assertThat(jdbc.queryForObject("SELECT status FROM scheduling_slot WHERE id=?", String.class, id))
                    .isEqualTo("OPEN");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"batches", "replacements"})
    void pastDatesAndAlreadyStartedTimesStillRejectWholeBatch(String endpoint) throws Exception {
        String coach = account("COACH");
        String mountain = mountain(coach);
        publish(endpoint, coach, batch(List.of(day(32), day(-1)), "10:00", "12:00", mountain), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("不能选择过去的日期"));
        publish(endpoint, coach, batch(List.of(day(32), day(0)), "10:00", "12:00", mountain), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("只能发布未来时段"));
        assertThat(slotCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scheduling_batch", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scheduling_day", Integer.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"batches", "replacements"})
    void futureDatesKeepBatchAndQueryCapacityLimits(String endpoint) throws Exception {
        String coach = account("COACH");
        String mountain = mountain(coach);
        publish(endpoint, coach, batch(days(32, 32), "10:00", "12:00", mountain), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("每批请选择 1–31 个日期"));
        publish(endpoint, coach, batch(days(32, 17), "09:00", "21:00", mountain), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("每批最多发布 100 个时段"));
        publish(endpoint, coach, batch(List.of(day(32), day(32)), "10:00", "12:00", mountain), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("同一批次不能重复选择日期"));
        assertThat(slotCount()).isZero();
        publish(endpoint, coach, batch(days(32, 25), "09:00", "17:00", mountain), UUID.randomUUID().toString())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.slots.length()").value(100));
        publish(endpoint, coach, batch(days(90, 31), "10:00", "12:00", mountain), UUID.randomUUID().toString())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.slots.length()").value(31));
        String student = account("STUDENT");
        mvc.perform(get("/api/slots").with(user(student).roles("STUDENT"))
                        .param("from", day(90)).param("to", day(120)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/slots").with(user(student).roles("STUDENT"))
                        .param("from", day(90)).param("to", day(121)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("日期范围最多 31 天"));
        assertThat(slotCount()).isEqualTo(131);
    }

    @ParameterizedTest
    @ValueSource(strings = {"batches", "replacements"})
    void daylightSavingInvalidRangesStillRejectTheWholeBatch(String endpoint) throws Exception {
        String coach = account("COACH");
        String mountain = mountain(coach);
        for (String date : List.of("2026-11-01", "2027-03-14")) {
            publish(endpoint, coach, batch(List.of(day(32), date), "01:00", "05:00", mountain), UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest());
        }
        assertThat(slotCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scheduling_day", Integer.class)).isZero();
    }

    private ResultActions publish(String endpoint, String coach, String body, String key) throws Exception {
        return mvc.perform(post("/api/coach/availability/" + endpoint).with(user(coach).roles("COACH")).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int slotCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM scheduling_slot", Integer.class); }

    private String mountain(String coach) throws Exception {
        String result = mvc.perform(post("/api/coach/mountains").with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"R5 mountain %s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(result, "$.id");
    }

    private String course(String coach) throws Exception {
        String result = mvc.perform(post("/api/coach/courses").with(user(coach).roles("COACH")).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"远期单板课\",\"description\":\"\",\"priceAmount\":\"150.00\",\"currency\":\"CAD\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(result, "$.id");
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
                role.equals("COACH") ? null : "BEGINNER", role, "test-hash", NOW, NOW);
        return id;
    }

    private static String batch(List<String> dates, String start, String end, String mountain) {
        return "{\"days\":[" + String.join(",", dates.stream().map(date ->
                "{\"localDate\":\"%s\",\"startTime\":\"%s\",\"endTime\":\"%s\",\"mountainId\":\"%s\"}"
                        .formatted(date, start, end, mountain)).toList()) + "]}";
    }

    private static List<String> days(int start, int count) {
        return IntStream.range(start, start + count).mapToObj(AvailabilityRevision5ApiTest::day).toList();
    }
    private static String day(int offset) { return TODAY.plusDays(offset).toString(); }
}
