package com.geer.snowboard.v2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.geer.snowboard.v2.identity.application.port.out.PasswordResetCodeCodec;
import com.geer.snowboard.v2.identity.application.port.out.VerificationTokenCodec;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {"identity.mail.worker.enabled=false", "spring.session.jdbc.cleanup-cron=-"})
@AutoConfigureMockMvc
@Testcontainers
class PasswordRecoveryApiTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("password_recovery_test").withUsername("password_recovery_test").withPassword("test_password");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired VerificationTokenCodec verificationTokens;
    @Autowired PasswordResetCodeCodec resetCodes;

    @Test
    void requestUsesNeutralResponseAndStillRequiresCsrf() throws Exception {
        String body = "{\"email\":\"unknown@recovery.example\"}";
        mvc.perform(post("/api/auth/password-recovery/request")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/password-recovery/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void invalidCodeAndMissingGrantCannotResetAPassword() throws Exception {
        mvc.perform(post("/api/auth/password-recovery/verify").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@recovery.example\",\"code\":\"00000000\"}"))
                .andExpect(status().isGone());
        mvc.perform(post("/api/auth/password-recovery/complete").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"12345678\",\"confirmPassword\":\"12345678\"}"))
                .andExpect(status().isGone());
    }

    @Test
    void malformedRecoveryBodiesReturnBadRequest() throws Exception {
        for (String path : new String[]{"request", "verify", "complete"}) {
            mvc.perform(post("/api/auth/password-recovery/" + path).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("null"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void resetRevokesEveryPreviousLoginSessionAndRequiresTheNewPassword() throws Exception {
        String email = "sessions-" + UUID.randomUUID() + "@example.test";
        String oldPassword = "old pass 123";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rider\",\"level\":\"入门\",\"email\":\"" + email
                        + "\",\"password\":\"" + oldPassword + "\"}"))
                .andExpect(status().isOk());
        String verificationId = jdbc.queryForObject("""
                SELECT v.id FROM identity_verification v JOIN identity_account a ON a.id=v.account_id
                WHERE a.email_key=?
                """, String.class, email);
        mvc.perform(post("/api/auth/email-verification").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + verificationTokens.issue(verificationId) + "\"}"))
                .andExpect(status().isOk());
        String loginBody = "{\"email\":\"" + email + "\",\"password\":\"" + oldPassword + "\"}";
        var first = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody)).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        var second = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody)).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        mvc.perform(get("/api/auth/me").cookie(first)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(second)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/password-recovery/request").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        String resetId = jdbc.queryForObject("""
                SELECT r.id FROM identity_password_reset r JOIN identity_account a ON a.id=r.account_id
                WHERE a.email_key=?
                """, String.class, email);
        var initialCsrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var initialCookie = initialCsrf.getResponse().getCookie("SESSION");
        String initialToken = JsonPath.read(initialCsrf.getResponse().getContentAsString(), "$.token");
        var grantCookie = mvc.perform(post("/api/auth/password-recovery/verify")
                .cookie(initialCookie).header("X-CSRF-TOKEN", initialToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + resetCodes.issue(resetId) + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertThat(grantCookie).isNotNull();
        var grantCsrf = mvc.perform(get("/api/auth/csrf").cookie(grantCookie)).andExpect(status().isOk()).andReturn();
        String grantToken = JsonPath.read(grantCsrf.getResponse().getContentAsString(), "$.token");
        assertThat(grantToken).isNotEqualTo(initialToken);
        var secondGrantCookie = mvc.perform(post("/api/auth/password-recovery/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + resetCodes.issue(resetId) + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        var loginWithGrant = mvc.perform(post("/api/auth/login").with(csrf()).cookie(secondGrantCookie)
                .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        mvc.perform(post("/api/auth/password-recovery/complete").with(csrf()).cookie(loginWithGrant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"new pass 456\",\"confirmPassword\":\"new pass 456\"}"))
                .andExpect(status().isGone());
        var thirdGrantCookie = mvc.perform(post("/api/auth/password-recovery/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + resetCodes.issue(resetId) + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        mvc.perform(post("/api/auth/password-recovery/request").with(csrf()).cookie(thirdGrantCookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/password-recovery/complete").with(csrf()).cookie(thirdGrantCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"new pass 456\",\"confirmPassword\":\"new pass 456\"}"))
                .andExpect(status().isGone());
        var fourthGrantCookie = mvc.perform(post("/api/auth/password-recovery/verify").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + resetCodes.issue(resetId) + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        String wrongCode = resetCodes.issue(resetId).equals("00000000") ? "11111111" : "00000000";
        mvc.perform(post("/api/auth/password-recovery/verify").with(csrf()).cookie(fourthGrantCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + wrongCode + "\"}"))
                .andExpect(status().isGone());
        mvc.perform(post("/api/auth/password-recovery/complete").with(csrf()).cookie(fourthGrantCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"new pass 456\",\"confirmPassword\":\"new pass 456\"}"))
                .andExpect(status().isGone());
        mvc.perform(post("/api/auth/password-recovery/complete").with(csrf()).cookie(first)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"new pass 456\",\"confirmPassword\":\"new pass 456\"}"))
                .andExpect(status().isGone());
        mvc.perform(post("/api/auth/password-recovery/complete").cookie(grantCookie)
                .header("X-CSRF-TOKEN", grantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"new pass 456\",\"confirmPassword\":\"mismatch\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/password-recovery/complete").cookie(grantCookie)
                .header("X-CSRF-TOKEN", grantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"new pass 456\",\"confirmPassword\":\"new pass 456\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(second)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(grantCookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginBody)).andExpect(status().isUnauthorized());
        var newLogin = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"new pass 456\"}"))
                .andExpect(status().isOk()).andReturn();
        var newCookie = newLogin.getResponse().getCookie("SESSION");
        String sessionId = new String(Base64.getDecoder().decode(newCookie.getValue()), StandardCharsets.UTF_8);
        assertThat(jdbc.update("""
                DELETE a FROM SPRING_SESSION_ATTRIBUTES a JOIN SPRING_SESSION s ON s.PRIMARY_ID=a.SESSION_PRIMARY_ID
                WHERE s.SESSION_ID=? AND a.ATTRIBUTE_NAME='identity.credentialVersion'
                """, sessionId)).isEqualTo(1);
        mvc.perform(get("/api/auth/me").cookie(newCookie)).andExpect(status().isUnauthorized());
    }
}
