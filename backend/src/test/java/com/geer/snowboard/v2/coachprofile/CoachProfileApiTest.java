package com.geer.snowboard.v2.coachprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {"identity.mail.worker.enabled=false", "booking.mail.worker.enabled=false",
        "media.worker.enabled=false", "spring.session.jdbc.cleanup-cron=-"})
@AutoConfigureMockMvc
@Testcontainers
class CoachProfileApiTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("profile_test").withUsername("profile_test").withPassword("test_password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void resetProfile() {
        jdbc.update("DELETE FROM coach_profile_publish_request");
        jdbc.update("DELETE FROM coach_profile_page");
        jdbc.update("DELETE FROM media_reference");
    }

    @Test void savesIndependentSocialTextAndIgnoresLegacyWechatAccount() throws Exception {
        String coach = account("COACH");
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        long version = 0;
        for (var entry : java.util.Map.of("xhsAccount", "小红书 自由昵称", "douyinAccount", "抖音号 @geer",
                "xhsUrl", "http://short.example.test/profile", "douyinUrl", "主页稍后补充").entrySet()) {
            String body = json.writeValueAsString(java.util.Map.of("expectedVersion", version++, "content",
                    java.util.Map.of("displayName", "GEER", entry.getKey(), entry.getValue(), "wechatId", "legacy-wechat")));
            mvc.perform(put("/api/coach/profile/draft").with(user(coach).roles("COACH")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content." + entry.getKey()).value(entry.getValue()))
                    .andExpect(jsonPath("$.content.wechatId").doesNotExist());
        }
    }

    @Test void anonymousReadsOnlyPublishedProfileAndCannotReadDraft() throws Exception {
        mvc.perform(get("/api/coach-profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));
        mvc.perform(get("/api/coach/profile/draft")).andExpect(status().isUnauthorized());
        String student = account("STUDENT");
        mvc.perform(get("/api/coach/profile/draft").with(user(student).roles("STUDENT")))
                .andExpect(status().isForbidden());
    }
    @Test void draftRequiresVersionAndDoesNotPublishUntilComplete() throws Exception {
        String coach = account("COACH");
        var draft = mvc.perform(get("/api/coach/profile/draft").with(user(coach).roles("COACH")))
                .andExpect(status().isOk()).andReturn();
        Number version = JsonPath.read(draft.getResponse().getContentAsString(), "$.version");
        String body = "{\"expectedVersion\":" + version + ",\"content\":{\"displayName\":\"GEER\",\"tagline\":\"练习与热爱\",\"bio\":\"草稿私有简介\"}}";
        mvc.perform(put("/api/coach/profile/draft").with(user(coach).roles("COACH"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/coach/profile/draft").with(user(coach).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.bio").value("草稿私有简介"));
        mvc.perform(put("/api/coach/profile/draft").with(user(coach).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        var publicPage = mvc.perform(get("/api/coach-profile")).andExpect(status().isOk()).andReturn();
        assertThat(publicPage.getResponse().getContentAsString()).doesNotContain("草稿私有简介");
        mvc.perform(post("/api/coach/profile/publish").with(user(coach).roles("COACH")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"draftVersion\":" + (version.longValue() + 1) + "}"))
                .andExpect(status().isBadRequest()); // A hero image is required for publication.
    }
    private String account(String role) {
        if (role.equals("COACH")) {
            var existing = jdbc.queryForList("SELECT id FROM identity_account WHERE role='COACH'", String.class);
            if (!existing.isEmpty()) return existing.getFirst();
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identity_account (id,email,email_key,name,level,role,password_hash,verified_at,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id, id + "@test.invalid", id + "@test.invalid", "Test", role.equals("STUDENT") ? "BEGINNER" : null,
                role, "not-used", Instant.now(), Instant.now());
        return id;
    }
}
