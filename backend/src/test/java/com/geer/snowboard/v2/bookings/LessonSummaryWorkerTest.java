package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.*;
import com.geer.snowboard.v2.bookings.application.port.in.*;
import com.geer.snowboard.v2.bookings.application.port.out.*;
import com.geer.snowboard.v2.bookings.application.service.*;
import com.geer.snowboard.v2.bookings.adapter.out.mail.SmtpBookingMailSender;
import com.jayway.jsonpath.JsonPath;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties={"identity.mail.worker.enabled=false","booking.mail.worker.enabled=false",
        "spring.session.jdbc.cleanup-cron=-","identity.public-url=https://lessons.example.test"})
@Testcontainers
class LessonSummaryWorkerTest {
    @Container static final MySQLContainer mysql=new MySQLContainer("mysql:8.4")
            .withDatabaseName("summary_worker").withUsername("reminder_test").withPassword("test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",mysql::getJdbcUrl); r.add("spring.datasource.username",mysql::getUsername);
        r.add("spring.datasource.password",mysql::getPassword);
        r.add("identity.verification-key",()->"a-private-test-key-with-at-least-32-bytes");
    }
    static final class TestClock extends Clock {
        final AtomicReference<Instant> time=new AtomicReference<>();
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
        public Instant instant() { return time.get(); }
    }
    static final class Sender implements BookingMailSender {
        record Message(String email,String subject,String body) {}
        final List<Message> messages=new CopyOnWriteArrayList<>();
        final Set<String> failures=ConcurrentHashMap.newKeySet();
        BookingMailSender delegate;
        public void send(String email,String subject,String body) {
            if(failures.contains(email)) throw new IllegalStateException("fake SMTP failure");
            if(delegate!=null) delegate.send(email,subject,body);
            messages.add(new Message(email,subject,body));
        }
    }
    @TestConfiguration static class Overrides {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
        @Bean @Primary Sender testSender() { return new Sender(); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired BookingMailQueue queue;
    @Autowired BookingStore bookings;
    @Autowired com.geer.snowboard.v2.bookings.application.port.out.LessonSummaryStore summaries;
    @Autowired LessonSummaryReminderOperations reminders;
    @Autowired LessonSummaryOperations summaryOperations;
    @Autowired BookingMailOperations worker;
    @Autowired BookingMailContacts contacts;
    @Autowired BookingLinkBase links;
    @Autowired TestClock clock;
    @Autowired Sender sender;
    @BeforeEach void reset() {
        clock.time.set(Instant.parse("2030-01-07T12:00:00Z"));
        sender.messages.clear(); sender.failures.clear(); sender.delegate=null;
        jdbc.update("DELETE FROM bookings_lesson_summary"); jdbc.update("DELETE FROM bookings_mail_task"); jdbc.update("DELETE FROM bookings_request");
    }

    private Instant end(Fixture f) { return bookings.findById(f.bookingId()).endAt(); }
    private void plan(Fixture f) { reminders.scheduleConfirmed(f.bookingId(),clock.instant()); }
    private String state(Fixture f,String event) { return jdbc.queryForObject("SELECT status FROM bookings_mail_task WHERE booking_id=? AND event_type=?",String.class,f.bookingId(),event); }
    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task",Long.class); }
    private com.geer.snowboard.v2.sharedkernel.Actor actor(Fixture f) { return new com.geer.snowboard.v2.sharedkernel.Actor(f.coachId(),"COACH","教练"); }
    private LessonSummaryOperations.Write content(long version) { return new LessonSummaryOperations.Write("PRIVATE练习","PRIVATE加强","PRIVATE作业",version); }
    private void publish(Fixture f) { summaryOperations.publish(actor(f),f.bookingId(),content(0)); }

    @Test void atEndSendsCoachOnceEvenWithDraftThenFirstPublishNotifiesOnlyStudent() {
        var f=booking("CONFIRMED");plan(f);
        clock.time.set(end(f).minusSeconds(1));worker.runOnce();assertThat(sender.messages).isEmpty();
        clock.time.set(end(f));summaryOperations.save(actor(f),f.bookingId(),content(0));worker.runOnce();worker.runOnce();
        assertThat(sender.messages).hasSize(1);var coach=sender.messages.getFirst();
        assertThat(coach.email()).isEqualTo(f.coachId()+"@example.test");
        assertThat(coach.subject()).isEqualTo("GEER 课后提醒：请填写课程总结");
        assertThat(coach.body()).contains("学员：测试学员","单板课程","Blue Mountain","America/Toronto","2030-01-12 12:00 -05:00","/#/coach-lesson-summaries/"+f.bookingId()).doesNotContain("PRIVATE","+1416555","@example.test");
        summaryOperations.publish(actor(f),f.bookingId(),content(1));worker.runOnce();
        assertThat(sender.messages).hasSize(2);var student=sender.messages.getLast();
        assertThat(student.email()).isEqualTo(f.studentId()+"@example.test");assertThat(student.subject()).isEqualTo("GEER 课程总结已发布");
        assertThat(student.body()).contains("/#/completed-lessons/"+f.bookingId()).doesNotContain("PRIVATE","+1416555","@example.test");
        summaryOperations.save(actor(f),f.bookingId(),new LessonSummaryOperations.Write("修改","加强","作业",2L));worker.runOnce();assertThat(sender.messages).hasSize(2);
    }
    @Test void publishedCancelledRejectedWrongRecipientAndChangedEndAreSkipped() {
        var published=booking("CONFIRMED");plan(published);
        var cancelled=booking("CONFIRMED");plan(cancelled);
        var rejected=booking("CONFIRMED");plan(rejected);
        var wrong=booking("CONFIRMED");plan(wrong);
        var changed=booking("CONFIRMED");plan(changed);
        jdbc.update("UPDATE bookings_request SET status='CANCELLED_BY_STUDENT' WHERE id=?",cancelled.bookingId());
        jdbc.update("UPDATE bookings_request SET status='REJECTED' WHERE id=?",rejected.bookingId());
        jdbc.update("UPDATE bookings_mail_task SET recipient_account_id=? WHERE booking_id=?",wrong.studentId(),wrong.bookingId());
        jdbc.update("UPDATE bookings_request SET end_at_utc_snapshot=? WHERE id=?",utc(end(changed).plusSeconds(60)),changed.bookingId());
        clock.time.set(end(published));publish(published);worker.runOnce();
        assertThat(sender.messages).hasSize(1);assertThat(sender.messages.getFirst().subject()).isEqualTo("GEER 课程总结已发布");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE status='SKIPPED'",Integer.class)).isEqualTo(5);
    }
    @Test void noTwentyFourHourExpiryAndEightFailuresBecomeDeadWithBoundedBackoff() {
        var f=booking("CONFIRMED");plan(f);clock.time.set(end(f).plusSeconds(3*86400));sender.failures.add(f.coachId()+"@example.test");
        long[] delays={60,120,240,480,960,1920,3600,3600};
        for(long delay:delays) {
            Instant attempted=clock.instant();worker.runOnce();
            Instant next=jdbc.queryForObject("SELECT next_attempt_at FROM bookings_mail_task WHERE booking_id=?",(rs,row)->rs.getObject(1,LocalDateTime.class).toInstant(ZoneOffset.UTC),f.bookingId());
            assertThat(next).isEqualTo(attempted.plusSeconds(delay));clock.time.set(next);
        }
        assertThat(state(f,BookingMailQueue.COACH_SUMMARY_REMINDER)).isEqualTo("DEAD");worker.cleanup();assertThat(count()).isEqualTo(1);
    }
    @Test void independentFeatureSwitchPausesPlanningAndClaimButStudentPublicationStillSends() {
        var f=booking("CONFIRMED");plan(f);clock.time.set(end(f));
        new BookingMailWorker(queue,bookings,contacts,sender,clock,links,new BookingReminderSettings(false),summaries,new LessonSummarySettings(false)).runOnce();
        assertThat(sender.messages).isEmpty();
        var published=booking("CONFIRMED");clock.time.set(end(published));publish(published);
        new BookingMailWorker(queue,bookings,contacts,sender,clock,links,new BookingReminderSettings(false),summaries,new LessonSummarySettings(false)).runOnce();
        assertThat(sender.messages).hasSize(1);assertThat(state(f,BookingMailQueue.COACH_SUMMARY_REMINDER)).isEqualTo("PENDING");
        var fresh=booking("CONFIRMED");new LessonSummaryReminderService(bookings,summaries,queue,new LessonSummarySettings(false),clock).scheduleConfirmed(fresh.bookingId(),clock.instant());
        assertThat(count()).isEqualTo(2);worker.runOnce();assertThat(sender.messages).hasSize(2);
    }
    @Test void boundedBackfillRecoversDowntimeAndDurableMarkerSurvivesTaskCleanup() {
        for(int i=0;i<53;i++) booking("CONFIRMED");
        clock.time.set(clock.instant().plusSeconds(20*86400));reminders.backfill();assertThat(count()).isEqualTo(50);
        reminders.backfill();assertThat(count()).isEqualTo(53);reminders.backfill();assertThat(count()).isEqualTo(53);
        jdbc.update("UPDATE bookings_mail_task SET status='SENT',created_at=?",utc(clock.instant().minusSeconds(8*86400)));
        worker.cleanup();assertThat(count()).isZero();reminders.backfill();assertThat(count()).isZero();
    }
    @Test void oldMarkedLessonsAndLateConfirmationNeverInventCoachReminderButCanPublish() {
        var old=booking("CONFIRMED");jdbc.update("UPDATE bookings_request SET lesson_summary_reminder_planned_at=? WHERE id=?",utc(clock.instant()),old.bookingId());
        var late=booking("CONFIRMED");clock.time.set(end(late));plan(late);reminders.backfill();assertThat(count()).isZero();
        publish(old);worker.runOnce();assertThat(sender.messages).hasSize(1);assertThat(sender.messages.getFirst().subject()).isEqualTo("GEER 课程总结已发布");
    }
    @Test void confirmationAfterEndWhilePausedStaysExcludedWhenRemindersResume() {
        var f=booking("CONFIRMED");clock.time.set(end(f));
        new LessonSummaryReminderService(bookings,summaries,queue,new LessonSummarySettings(false),clock).scheduleConfirmed(f.bookingId(),clock.instant());
        reminders.backfill();assertThat(count()).isZero();
    }
    @Test void planningFailureRollsBackTaskAndMarker() {
        var f=booking("CONFIRMED");jdbc.execute("ALTER TABLE bookings_mail_task ADD CONSTRAINT ck_summary_plan_failure CHECK(event_type<>'COACH_SUMMARY_REMINDER')");
        try {assertThatThrownBy(()->plan(f)).isInstanceOf(RuntimeException.class);assertThat(count()).isZero();
            assertThat(jdbc.queryForObject("SELECT lesson_summary_reminder_planned_at FROM bookings_request WHERE id=?",Object.class,f.bookingId())).isNull();
        } finally {jdbc.execute("ALTER TABLE bookings_mail_task DROP CHECK ck_summary_plan_failure");}
    }
    @Test void concurrentBackfillAndPublishSerializeOnBookingAndNeverSendObsoleteCoachNotice() throws Exception {
        var f=booking("CONFIRMED");clock.time.set(end(f));var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await();reminders.backfill();return true;});
            var b=pool.submit(()->{gate.await();publish(f);return true;});gate.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        reminders.backfill();worker.runOnce();assertThat(sender.messages).hasSize(1);assertThat(sender.messages.getFirst().subject()).isEqualTo("GEER 课程总结已发布");
    }
    @Test void expiredLeaseRejectsStaleTokenAndPublicationDuringLookupIsRechecked() {
        var f=booking("CONFIRMED");plan(f);clock.time.set(end(f));var old=queue.claim(clock.instant());
        assertThat(old.claimUntil()).isEqualTo(clock.instant().plusSeconds(120));clock.time.set(old.claimUntil());var fresh=queue.claim(clock.instant());
        queue.sent(old,clock.instant());queue.skipped(old);queue.failed(old,clock.instant(),"stale",true);
        assertThat(state(f,BookingMailQueue.COACH_SUMMARY_REMINDER)).isEqualTo("CLAIMED");queue.failed(fresh,clock.instant(),"retry",false);
        BookingMailContacts raced=id->{publish(f);return contacts.emailForAccount(id);};
        new BookingMailWorker(queue,bookings,raced,sender,clock,links,new BookingReminderSettings(true),summaries,new LessonSummarySettings(true)).runOnce();
        assertThat(state(f,BookingMailQueue.COACH_SUMMARY_REMINDER)).isEqualTo("SKIPPED");
        assertThat(sender.messages).hasSize(1);assertThat(sender.messages.getFirst().subject()).isEqualTo("GEER 课程总结已发布");
    }
    @Test void smtpActuallyDeliversBothSummaryMessagesToMailpitWithoutPrivateText() throws Exception {
        try(var mailpit=new GenericContainer<>("ghcr.io/axllent/mailpit:v1.31.3").withExposedPorts(1025,8025)) {
            mailpit.start();var mail=new JavaMailSenderImpl();mail.setHost(mailpit.getHost());mail.setPort(mailpit.getMappedPort(1025));
            var factory=new StaticListableBeanFactory();factory.addBean("mail",mail);
            sender.delegate=new SmtpBookingMailSender(factory.getBeanProvider(JavaMailSender.class),new MockEnvironment().withProperty("identity.mail.from","geer@example.test"));
            var f=booking("CONFIRMED");plan(f);clock.time.set(end(f));worker.runOnce();publish(f);worker.runOnce();
            var client=HttpClient.newHttpClient();String base="http://"+mailpit.getHost()+":"+mailpit.getMappedPort(8025);
            String json=client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/messages")).GET().build(),HttpResponse.BodyHandlers.ofString()).body();
            assertThat((Integer)JsonPath.read(json,"$.total")).isEqualTo(2);
            List<String> ids=JsonPath.read(json,"$.messages[*].ID");
            Set<String> subjects=new HashSet<>();Set<String> recipients=new HashSet<>();
            for(String id:ids) {
                String message=client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/message/"+id)).GET().build(),HttpResponse.BodyHandlers.ofString()).body();
                subjects.add(JsonPath.read(message,"$.Subject"));recipients.addAll(JsonPath.<List<String>>read(message,"$.To[*].Address"));
                assertThat((String)JsonPath.read(message,"$.Text")).contains("单板课程","Blue Mountain","America/Toronto","-05:00","https://lessons.example.test/#/").doesNotContain("PRIVATE","+1416555","@example.test");
            }
            assertThat(subjects).containsExactlyInAnyOrder("GEER 课后提醒：请填写课程总结","GEER 课程总结已发布");
            assertThat(recipients).containsExactlyInAnyOrder(f.studentId()+"@example.test",f.coachId()+"@example.test");
            assertThat(state(f,BookingMailQueue.COACH_SUMMARY_REMINDER)).isEqualTo("SENT");assertThat(state(f,BookingMailQueue.STUDENT_SUMMARY_PUBLISHED)).isEqualTo("SENT");
        }
    }
    private Fixture booking(String status) {
        String coachId = jdbc.query("SELECT id FROM identity_account WHERE role='COACH'",
                rs -> rs.next() ? rs.getString(1) : null);
        if (coachId == null) {
            coachId = UUID.randomUUID().toString();
            account(coachId, "COACH", null);
        }
        String studentId = UUID.randomUUID().toString();
        account(studentId, "STUDENT", "BEGINNER");
        String courseId = UUID.randomUUID().toString();
        String slotId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();
        LocalDate date = LocalDate.now(clock.withZone(ZoneId.of("America/Toronto"))).plusDays(5);
        Instant start = date.atTime(LocalTime.of(10, 0)).atZone(ZoneId.of("America/Toronto")).toInstant();
        Instant end = start.plusSeconds(7200);
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO catalog_course
                (id,coach_id,title,description,price_amount,currency,idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,'单板课程','',150.00,'CAD',?,REPEAT('a',64),?)
                """, courseId, coachId, UUID.randomUUID().toString(), now);
        jdbc.update("""
                INSERT INTO scheduling_slot
                (id,coach_id,course_id,location,zone_id,local_date,start_at_utc,end_at_utc,status,
                 idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,?,'Blue Mountain','America/Toronto',?,?,?,'OPEN',?,REPEAT('b',64),?)
                """, slotId, coachId, courseId, date, utc(start), utc(end), UUID.randomUUID().toString(), now);
        jdbc.update("""
                INSERT INTO bookings_request
                (id,slot_id,course_id,coach_id,student_id,student_name_snapshot,status,course_title_snapshot,
                 price_amount_snapshot,currency_snapshot,location_snapshot,zone_id_snapshot,local_date_snapshot,
                 start_at_utc_snapshot,end_at_utc_snapshot,idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,?,?,?,'测试学员',?,'单板课程',150.00,'CAD','Blue Mountain','America/Toronto',?,?,?,?,
                        REPEAT('c',64),?)
                """, bookingId, slotId, courseId, coachId, studentId, status, date, utc(start), utc(end),
                UUID.randomUUID().toString(), now);
        jdbc.update("UPDATE bookings_request SET decided_at=? WHERE id=?",utc(now),bookingId);
        return new Fixture(bookingId, coachId, studentId);
    }

    private void account(String id, String role, String level) {
        jdbc.update("""
                INSERT INTO identity_account
                (id,email,email_key,name,level,role,password_hash,verified_at,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, id + "@example.test", id + "@example.test", role, level,
                role, "test-hash", clock.instant(), clock.instant());
    }

    private static LocalDateTime utc(Instant point) {
        return LocalDateTime.ofInstant(point, ZoneOffset.UTC);
    }

    private record Fixture(String bookingId, String coachId, String studentId) {}
}
