package com.geer.snowboard.v2.media;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
class MediaUploadApiTest {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("upload_test").withUsername("upload_test").withPassword("test_password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("identity.verification-key", () -> "a-private-test-key-with-at-least-32-bytes");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Test void coachCannotRequestOversizedOrDisguisedUploadAndStudentsCannotUpload() throws Exception {
        String coach = account("COACH"), student = account("STUDENT");
        mvc.perform(post("/api/coach/media/uploads").with(user(coach).roles("COACH")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"HERO\",\"contentType\":\"image/png\",\"size\":8388609}"))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/coach/media/uploads").with(user(coach).roles("COACH")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"HERO\",\"contentType\":\"image/svg+xml\",\"size\":10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/coach/media/uploads").with(user(student).roles("STUDENT")).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"HERO\",\"contentType\":\"image/png\",\"size\":10}"))
                .andExpect(status().isForbidden());
    }
    private String account(String role) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identity_account (id,email,email_key,name,level,role,password_hash,verified_at,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id,id+"@test.invalid",id+"@test.invalid","Test",role.equals("STUDENT")?"BEGINNER":null,
                role,"not-used",Instant.now(),Instant.now()); return id;
    }
}
