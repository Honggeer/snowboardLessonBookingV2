package com.geer.snowboard.v2.scheduling.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.geer.snowboard.v2.bootstrap.SecurityConfig;
import com.geer.snowboard.v2.bootstrap.TimeConfig;
import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import com.geer.snowboard.v2.scheduling.adapter.out.memory.InMemoryTimeWindowPreviewStore;
import com.geer.snowboard.v2.scheduling.application.service.TimeWindowPreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TimeWindowPreviewController.class)
@Import({SecurityConfig.class, TimeConfig.class, TimeWindowPreviewService.class,
        InMemoryTimeWindowPreviewStore.class})
@ActiveProfiles("local")
class TimeWindowPreviewControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private IdentityOperations identity;

    @Test
    void anonymousRequestCannotCreatePreview() throws Exception {
        mvc.perform(post("/api/demo/time-window-previews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestNeedsCsrfToken() throws Exception {
        mvc.perform(post("/api/demo/time-window-previews")
                        .with(user("coach"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedClientCanFetchCsrfTokenAndCreatePreview() throws Exception {
        var csrfResult = mvc.perform(get("/api/demo/time-window-previews/csrf")
                        .with(user("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String token = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        var session = (MockHttpSession) csrfResult.getRequest().getSession(false);

        mvc.perform(post("/api/demo/time-window-previews")
                        .with(user("coach"))
                        .session(session)
                        .header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated());
    }

    @Test
    void createsAndReadsPreviewWithExplicitUtcTimes() throws Exception {
        String location = mvc.perform(post("/api/demo/time-window-previews")
                        .with(user("coach"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationSeconds").value(5400))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location).with(user("coach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-12-01T14:00:00Z"));
    }

    @Test
    void invalidWindowIsRejected() throws Exception {
        mvc.perform(post("/api/demo/time-window-previews")
                        .with(user("coach"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":\"2026-12-01T14:00:00Z\",\"end\":\"2026-12-01T14:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPreviewReturnsNotFound() throws Exception {
        mvc.perform(get("/api/demo/time-window-previews/missing").with(user("coach")))
                .andExpect(status().isNotFound());
    }

    private static String validRequest() {
        return "{\"start\":\"2026-12-01T14:00:00Z\",\"end\":\"2026-12-01T15:30:00Z\"}";
    }
}
