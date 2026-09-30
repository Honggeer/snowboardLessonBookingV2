package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.geer.snowboard.v2.identity.application.port.out.VerificationTokenCodec;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = "identity.mail.worker.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class IdentityApiTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("identity_api_test").withUsername("identity_api_test").withPassword("test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired VerificationTokenCodec tokens;
    @Autowired MutableClock clock;
    @Autowired ApplicationContext context;

    @TestConfiguration
    static class ClockOverride {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        final AtomicLong offset = new AtomicLong();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.now().plusMillis(offset.get()); }
    }

    @Test
    void csrfProtectsRegistrationAndPublicInputCannotCreateCoach() throws Exception {
        String body = """
                {"name":"Geer","level":"入门","email":"geer@api.example","password":"snowboard password 雪山 2026","role":"COACH"}
                """;
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT role FROM identity_account WHERE email_key='geer@api.example'", String.class))
                .isEqualTo("STUDENT");
    }

    @Test
    void browserCsrfTokenWorksAndLoginRotatesTheSessionId() throws Exception {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        var initial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String csrfToken = JsonPath.read(initial.getResponse().getContentAsString(), "$.token");
        var firstCookie = initial.getResponse().getCookie("SESSION");
        assertThat(firstCookie).isNotNull();
        String email = "browser@api.example";
        String password = "snowboard password 雪山 2026";
        mvc.perform(post("/api/auth/register").cookie(firstCookie).header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Browser","level":"入门","email":"%s","password":"%s"}
                        """.formatted(email, password))).andExpect(status().isOk());
        String id = jdbc.queryForObject("SELECT id FROM identity_verification WHERE account_id=(SELECT id FROM identity_account WHERE email_key=?)", String.class, email);
        mvc.perform(post("/api/auth/email-verification").cookie(firstCookie).header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"token":"%s"}
                        """.formatted(tokens.issue(id)))).andExpect(status().isOk());
        var login = mvc.perform(post("/api/auth/login").cookie(firstCookie).header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON).content(loginBody(email, password)))
                .andExpect(status().isOk()).andReturn();
        var newCookie = login.getResponse().getCookie("SESSION");
        assertThat(newCookie).isNotNull();
        assertThat(newCookie.getValue()).isNotEqualTo(firstCookie.getValue());
        String persistedId = new String(Base64.getDecoder().decode(newCookie.getValue()), StandardCharsets.UTF_8);
        assertThat(jdbc.queryForObject("SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE SESSION_ID=?",
                Integer.class, persistedId)).isEqualTo(1800);
        String setCookie = login.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("HttpOnly", "SameSite=Lax", "Secure");
        mvc.perform(post("/api/auth/logout").cookie(newCookie).header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/csrf").cookie(newCookie)).andExpect(status().isOk());
    }

    @Test
    void verifiedStudentCanLoginAndReadOwnIdentity() throws Exception {
        String email = "other@api.example";
        String password = "snowboard password 雪山 2026";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Other","level":"进阶","email":"%s","password":"%s"}
                        """.formatted(email, password)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)))
                .andExpect(status().isUnauthorized());
        String id = jdbc.queryForObject("SELECT id FROM identity_verification WHERE account_id=(SELECT id FROM identity_account WHERE email_key=?)", String.class, email);
        mvc.perform(post("/api/auth/email-verification").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s"}
                        """.formatted(tokens.issue(id)))).andExpect(status().isOk());
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT")).andReturn();
        assertThat(login.getResponse().getCookie("SESSION")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class)).isGreaterThan(0);
        mvc.perform(get("/api/auth/me").cookie(login.getResponse().getCookie("SESSION")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Other"));
    }

    @Test
    void eightCharacterStudentPasswordRegistersAndLogsInWhileSevenIsRejected() throws Exception {
        String email = "eight@api.example";
        String body = """
                {"name":"Eight","level":"零基础","email":"%s","password":"%s"}
                """;
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(email, "雪".repeat(7))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(email, "雪".repeat(8))))
                .andExpect(status().isOk());
        String id = jdbc.queryForObject("SELECT id FROM identity_verification WHERE account_id=(SELECT id FROM identity_account WHERE email_key=?)", String.class, email);
        mvc.perform(post("/api/auth/email-verification").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s"}
                        """.formatted(tokens.issue(id)))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, "雪".repeat(8))))
                .andExpect(status().isOk());
    }

    private static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    @Test
    void loginExpiresAfterTwelveHoursAndLogoutInvalidatesServerSession() throws Exception {
        String email = "expiry@api.example";
        String password = "snowboard password 雪山 2026";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Expiry","level":"入门","email":"%s","password":"%s"}
                        """.formatted(email, password))).andExpect(status().isOk());
        String id = jdbc.queryForObject("SELECT id FROM identity_verification WHERE account_id=(SELECT id FROM identity_account WHERE email_key=?)", String.class, email);
        mvc.perform(post("/api/auth/email-verification").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s"}
                        """.formatted(tokens.issue(id)))).andExpect(status().isOk());
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password))).andExpect(status().isOk()).andReturn();
        var cookie = login.getResponse().getCookie("SESSION");
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk());
        try {
            clock.offset.set(13L * 3600 * 1000);
            mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
        } finally { clock.offset.set(0); }
        var secondLogin = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password))).andExpect(status().isOk()).andReturn();
        var secondCookie = secondLogin.getResponse().getCookie("SESSION");
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(secondCookie)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(secondCookie)).andExpect(status().isUnauthorized());
        var thirdLogin = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password))).andExpect(status().isOk()).andReturn();
        var thirdCookie = thirdLogin.getResponse().getCookie("SESSION");
        String sessionId = new String(Base64.getDecoder().decode(thirdCookie.getValue()), StandardCharsets.UTF_8);
        assertThat(jdbc.update("UPDATE SPRING_SESSION SET LAST_ACCESS_TIME=?,EXPIRY_TIME=? WHERE SESSION_ID=?",
                System.currentTimeMillis() - 31L * 60 * 1000, System.currentTimeMillis() - 1, sessionId)).isEqualTo(1);
        mvc.perform(get("/api/auth/me").cookie(thirdCookie)).andExpect(status().isUnauthorized());
    }
}
