package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties={"identity.mail.worker.enabled=false","booking.mail.worker.enabled=false","spring.session.jdbc.cleanup-cron=-"})
@AutoConfigureMockMvc
@Testcontainers
class LessonSummaryApiTest {
    @Container static final MySQLContainer mysql=new MySQLContainer("mysql:8.4")
            .withDatabaseName("lesson_summaries").withUsername("summary_test").withPassword("test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",mysql::getJdbcUrl); r.add("spring.datasource.username",mysql::getUsername);
        r.add("spring.datasource.password",mysql::getPassword);
        r.add("identity.verification-key",()->"a-private-test-key-with-at-least-32-bytes");
    }
    static final Instant NOW=Instant.parse("2030-01-07T18:00:00Z");
    @TestConfiguration static class TestTime {
        @Bean @Primary Clock summaryClock() { return Clock.fixed(NOW,ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.geer.snowboard.v2.bookings.application.port.in.LessonSummaryOperations summaries;


    @Test void completedLessonsAreFilteredByServerEndTimeAndStudentAcrossPages() throws Exception {
        String student=account("STUDENT"), other=account("STUDENT");
        var oldest=booking(student,"CONFIRMED",NOW.minusSeconds(7200));
        var latest=booking(student,"CONFIRMED",NOW);
        booking(student,"CONFIRMED",NOW.plusSeconds(1));
        for(String state:List.of("PENDING","REJECTED","CANCELLED_BY_STUDENT")) booking(student,state,NOW.minusSeconds(1));
        booking(other,"CONFIRMED",NOW);
        mvc.perform(get("/api/bookings/completed?limit=1").with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(latest.id()))
                .andExpect(jsonPath("$.items[0].summaryAvailable").value(false)).andExpect(jsonPath("$.nextCursor").value(latest.id()));
        mvc.perform(get("/api/bookings/completed?limit=1&cursor="+latest.id()).with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(oldest.id()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test void savesThreePrivateDraftFieldsThenPublishesAndUpdatesWithoutAnotherMail() throws Exception {
        String student=account("STUDENT"); var f=booking(student,"CONFIRMED",NOW);
        mvc.perform(patch("/api/coach/bookings/{id}/summary",f.id()).with(user(f.coach()).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(content("  前刃换刃\n重心移动  ","","",0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.practiceContent").value("前刃换刃\n重心移动")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/bookings/{id}/summary",f.id()).with(user(student).roles("STUDENT"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/coach/bookings/{id}/summary/publish",f.id()).with(user(f.coach()).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(content("换刃","重心","",1))).andExpect(status().isBadRequest());
        for(int i=0;i<2;i++) mvc.perform(post("/api/coach/bookings/{id}/summary/publish",f.id()).with(user(f.coach()).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(content("换刃","重心","复习换刃",1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PUBLISHED")).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/bookings/{id}/summary",f.id()).with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.practiceContent").value("换刃"))
                .andExpect(jsonPath("$.improvementAreas").value("重心")).andExpect(jsonPath("$.reinforcementExercises").value("复习换刃"));
        mvc.perform(patch("/api/coach/bookings/{id}/summary",f.id()).with(user(f.coach()).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(content("新练习","新重点","新课后练习",2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        mvc.perform(get("/api/bookings/{id}/summary",f.id()).with(user(student).roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.practiceContent").value("新练习"))
                .andExpect(jsonPath("$.improvementAreas").value("新重点"))
                .andExpect(jsonPath("$.reinforcementExercises").value("新课后练习"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=? AND event_type='STUDENT_SUMMARY_PUBLISHED'",Integer.class,f.id())).isEqualTo(1);
    }

    @Test void unfinishedOrCancelledLessonsCannotHaveASummary() throws Exception {
        String student=account("STUDENT");
        var fixtures=new ArrayList<Fixture>();
        fixtures.add(booking(student,"CONFIRMED",NOW.plusSeconds(1)));
        for(String state:List.of("PENDING","REJECTED","CANCELLED_BY_STUDENT")) fixtures.add(booking(student,state,NOW));
        for(var f:fixtures) mvc.perform(patch("/api/coach/bookings/{id}/summary",f.id()).with(user(f.coach()).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(content("a","b","c",0))).andExpect(status().isConflict());
    }
    @Test void enforcesOwnershipRoleSessionAndCsrfInHttpAndUseCases() throws Exception {
        var f=booking(account("STUDENT"),"CONFIRMED",NOW); var other=account("STUDENT");
        mvc.perform(get("/api/bookings/completed")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/coach/bookings/completed").with(user(f.student()).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/coach/bookings/{id}/summary",f.id()).with(user(f.coach()).roles("COACH"))
                .contentType(MediaType.APPLICATION_JSON).content(content("a","b","c",0))).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/{id}/summary",f.id()).with(user(other).roles("STUDENT"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/coach/bookings/{id}/summary",f.id()).with(user(other).roles("COACH"))).andExpect(status().isForbidden());
        assertThatThrownBy(()->summaries.save(new com.geer.snowboard.v2.sharedkernel.Actor(f.student(),"STUDENT","学员"),f.id(),
                new com.geer.snowboard.v2.bookings.application.port.in.LessonSummaryOperations.Write("a","b","c",0L)))
                .isInstanceOf(com.geer.snowboard.v2.sharedkernel.BusinessProblem.class);
        assertThatThrownBy(()->summaries.coachSummary(new com.geer.snowboard.v2.sharedkernel.Actor(other,"COACH","其他教练"),f.id()))
                .isInstanceOf(com.geer.snowboard.v2.sharedkernel.BusinessProblem.class);
        assertThatThrownBy(()->summaries.studentSummary(new com.geer.snowboard.v2.sharedkernel.Actor(other,"STUDENT","其他学员"),f.id()))
                .isInstanceOf(com.geer.snowboard.v2.sharedkernel.BusinessProblem.class);
        mvc.perform(get("/api/bookings/completed?cursor="+f.id()).with(user(other).roles("STUDENT"))).andExpect(status().isBadRequest());
    }
    @Test void unicodeLimitsAndEachRequiredFieldAreEnforcedWithoutTruncation() throws Exception {
        var f=booking(account("STUDENT"),"CONFIRMED",NOW);
        for(int missing=0;missing<3;missing++) {
            String[] values={"a","b","c"}; values[missing]=" ";
            mvc.perform(post("/api/coach/bookings/{id}/summary/publish",f.id()).with(user(f.coach()).roles("COACH")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(content(values[0],values[1],values[2],0))).andExpect(status().isBadRequest());
            values[missing]="🏂".repeat(3001);
            mvc.perform(patch("/api/coach/bookings/{id}/summary",f.id()).with(user(f.coach()).roles("COACH")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(content(values[0],values[1],values[2],0))).andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/coach/bookings/{id}/summary",f.id()).with(user(f.coach()).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(content("🏂".repeat(3000),"雪".repeat(3000),"c",0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.practiceContent").value("🏂".repeat(3000)));
    }
    @Test void conflictingConcurrentSavesNeverSilentlyOverwrite() throws Exception {
        var f=booking(account("STUDENT"),"CONFIRMED",NOW); var gate=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<java.util.concurrent.Future<Integer>>();
            for(String text:List.of("first","second")) futures.add(pool.submit(()->{
                gate.await(); return mvc.perform(patch("/api/coach/bookings/{id}/summary",f.id()).with(user(f.coach()).roles("COACH")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(content(text,"b","c",0))).andReturn().getResponse().getStatus();
            }));
            gate.countDown(); assertThat(List.of(futures.get(0).get(10,java.util.concurrent.TimeUnit.SECONDS),futures.get(1).get(10,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM bookings_lesson_summary WHERE booking_id=?",Long.class,f.id())).isEqualTo(1);
    }
    @Test void concurrentPublishIsIdempotentAndCleanupCannotResendPublishNotification() throws Exception {
        var f=booking(account("STUDENT"),"CONFIRMED",NOW); var gate=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<java.util.concurrent.Future<Integer>>();
            for(int i=0;i<2;i++) futures.add(pool.submit(()->{gate.await();return publish(f).andReturn().getResponse().getStatus();}));
            gate.countDown();for(var result:futures) assertThat(result.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=?",Integer.class,f.id())).isEqualTo(1);
        jdbc.update("DELETE FROM bookings_mail_task WHERE booking_id=?",f.id()); publish(f).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE booking_id=?",Integer.class,f.id())).isZero();
    }
    @Test void notificationFailureRollsBackSummaryAndPlanningMarker() throws Exception {
        var f=booking(account("STUDENT"),"CONFIRMED",NOW);
        jdbc.execute("ALTER TABLE bookings_mail_task ADD CONSTRAINT ck_summary_api_failure CHECK(event_type<>'STUDENT_SUMMARY_PUBLISHED' OR booking_id<>'"+f.id()+"')");
        try {
            publish(f).andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_lesson_summary WHERE booking_id=?",Integer.class,f.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT lesson_summary_reminder_planned_at FROM bookings_request WHERE id=?",Object.class,f.id())).isNull();
        } finally { jdbc.execute("ALTER TABLE bookings_mail_task DROP CHECK ck_summary_api_failure"); }
    }
    private org.springframework.test.web.servlet.ResultActions publish(Fixture f) throws Exception {
        return mvc.perform(post("/api/coach/bookings/{id}/summary/publish",f.id()).with(user(f.coach()).roles("COACH")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(content("a","b","c",0)));
    }

    static String content(String practice,String improvement,String exercises,long version) {
        return "{\"practiceContent\":"+quote(practice)+",\"improvementAreas\":"+quote(improvement)+",\"reinforcementExercises\":"+quote(exercises)+",\"expectedVersion\":"+version+"}";
    }
    private static String quote(String value) { return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\""; }
    record Fixture(String id,String coach,String student) {}
    Fixture booking(String student,String state,Instant end) {
        String coach=jdbc.query("SELECT id FROM identity_account WHERE role='COACH'",rs->rs.next()?rs.getString(1):null);
        if(coach==null) coach=account("COACH");
        String course=UUID.randomUUID().toString(),slot=UUID.randomUUID().toString(),id=UUID.randomUUID().toString();
        Instant start=end.minusSeconds(7200); LocalDate date=start.atZone(ZoneId.of("America/Toronto")).toLocalDate();
        jdbc.update("INSERT INTO catalog_course(id,coach_id,title,description,price_amount,currency,idempotency_key,request_fingerprint,created_at) VALUES (?,?,'基础课','',150,'CAD',?,REPEAT('a',64),?)",course,coach,UUID.randomUUID().toString(),utc(NOW));
        jdbc.update("INSERT INTO scheduling_slot(id,coach_id,course_id,location,zone_id,local_date,start_at_utc,end_at_utc,status,idempotency_key,request_fingerprint,created_at) VALUES (?,?,?,'Blue Mountain','America/Toronto',?,?,?,'OPEN',?,REPEAT('b',64),?)",slot,coach,course,date,utc(start),utc(end),UUID.randomUUID().toString(),utc(NOW));
        jdbc.update("""
                INSERT INTO bookings_request(id,slot_id,course_id,coach_id,student_id,student_name_snapshot,status,course_title_snapshot,
                    price_amount_snapshot,currency_snapshot,location_snapshot,zone_id_snapshot,local_date_snapshot,start_at_utc_snapshot,
                    end_at_utc_snapshot,idempotency_key,request_fingerprint,created_at,decided_at)
                VALUES (?,?,?,?,?,'测试学员',?,'基础课',150,'CAD','Blue Mountain','America/Toronto',?,?,?,?,REPEAT('c',64),?,?)
                """,id,slot,course,coach,student,state,date,utc(start),utc(end),UUID.randomUUID().toString(),utc(NOW),utc(start.minusSeconds(86400)));
        return new Fixture(id,coach,student);
    }
    String account(String role) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identity_account(id,email,email_key,name,level,role,password_hash,verified_at,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                id,id+"@example.test",id+"@example.test",role.equals("COACH")?"教练":"学员",role.equals("COACH")?null:"BEGINNER",role,"test-hash",utc(NOW),utc(NOW));
        return id;
    }
    static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant,ZoneOffset.UTC); }
}
