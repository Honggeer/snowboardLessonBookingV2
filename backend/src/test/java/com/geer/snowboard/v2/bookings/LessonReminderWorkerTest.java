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
class LessonReminderWorkerTest {
    @Container static final MySQLContainer mysql=new MySQLContainer("mysql:8.4")
            .withDatabaseName("reminder_worker").withUsername("reminder_test").withPassword("test_password");
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
    @Autowired BookingReminderOperations reminders;
    @Autowired BookingMailOperations worker;
    @Autowired BookingMailContacts contacts;
    @Autowired BookingLinkBase links;
    @Autowired TestClock clock;
    @Autowired Sender sender;
    @BeforeEach void reset() {
        clock.time.set(Instant.parse("2030-01-07T12:00:00Z"));
        sender.messages.clear(); sender.failures.clear(); sender.delegate=null;
        jdbc.update("DELETE FROM bookings_mail_task"); jdbc.update("DELETE FROM bookings_request");
    }
    private Instant start(Fixture f) { return bookings.findById(f.bookingId()).startAt(); }
    private Instant due(Fixture f) { return start(f).minusSeconds(40*3600); }
    private void plan(Fixture f) { reminders.scheduleConfirmed(f.bookingId(),clock.instant()); }
    private String state(Fixture f,String event) { return jdbc.queryForObject("SELECT status FROM bookings_mail_task WHERE booking_id=? AND event_type=?",String.class,f.bookingId(),event); }
    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task",Long.class); }

    @Test void waitsUntilToronto18AndSendsCorrectIndependentMessagesOnce() {
        var f=booking("CONFIRMED"); plan(f);
        clock.time.set(due(f).minusSeconds(1)); worker.runOnce(); assertThat(sender.messages).isEmpty();
        clock.time.set(due(f)); worker.runOnce(); worker.runOnce();
        assertThat(sender.messages).hasSize(2);
        assertThat(sender.messages).extracting(Sender.Message::email).containsExactlyInAnyOrder(f.studentId()+"@example.test",f.coachId()+"@example.test");
        for(var message:sender.messages) assertThat(message.body()).contains("单板课程","Blue Mountain","America/Toronto","2030-01-12 10:00 -05:00","2030-01-11 10:00 -05:00","距开课不足 24 小时无法取消预约")
                .doesNotContain("明天","+1416555","@example.test");
        assertThat(sender.messages.stream().filter(m->m.email().startsWith(f.studentId())).findFirst().orElseThrow().body())
                .contains("https://lessons.example.test/#/my-bookings/"+f.bookingId());
        assertThat(sender.messages.stream().filter(m->m.email().startsWith(f.coachId())).findFirst().orElseThrow().body())
                .contains("学员：测试学员","/#/coach-applications/"+f.bookingId());
    }
    @Test void oneRecipientFailureRetriesWithoutSendingTheSuccessfulRecipientAgain() {
        var f=booking("CONFIRMED"); plan(f); clock.time.set(due(f)); sender.failures.add(f.studentId()+"@example.test");
        worker.runOnce(); assertThat(state(f,BookingMailQueue.STUDENT_LESSON_REMINDER)).isEqualTo("PENDING");
        assertThat(state(f,BookingMailQueue.COACH_LESSON_REMINDER)).isEqualTo("SENT");
        sender.failures.clear(); clock.time.set(clock.instant().plusSeconds(60)); worker.runOnce();
        assertThat(sender.messages).hasSize(2); assertThat(state(f,BookingMailQueue.STUDENT_LESSON_REMINDER)).isEqualTo("SENT");
    }
    @Test void cancelsAndChangedScheduleInvalidateQueuedReminders() {
        var cancelled=booking("CONFIRMED"); plan(cancelled);
        var changed=booking("CONFIRMED"); plan(changed);
        jdbc.update("UPDATE bookings_request SET status='CANCELLED_BY_STUDENT' WHERE id=?",cancelled.bookingId());
        jdbc.update("UPDATE bookings_request SET start_at_utc_snapshot=? WHERE id=?",utc(start(changed).plusSeconds(60)),changed.bookingId());
        clock.time.set(due(cancelled)); worker.runOnce(); assertThat(sender.messages).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings_mail_task WHERE status='SKIPPED'",Integer.class)).isEqualTo(4);
    }
    @Test void at24HoursRemainingAnUnsentOrRetryingReminderExpires() {
        var f=booking("CONFIRMED"); plan(f); clock.time.set(start(f).minusSeconds(86400));
        worker.runOnce(); assertThat(sender.messages).isEmpty(); assertThat(state(f,BookingMailQueue.STUDENT_LESSON_REMINDER)).isEqualTo("SKIPPED");
    }
    @Test void wrongRecipientIsNotSentAndEighthFailureIsDead() {
        var f=booking("CONFIRMED"); plan(f); clock.time.set(due(f));
        jdbc.update("UPDATE bookings_mail_task SET recipient_account_id=? WHERE booking_id=? AND event_type=?",f.coachId(),f.bookingId(),BookingMailQueue.STUDENT_LESSON_REMINDER);
        sender.failures.add(f.coachId()+"@example.test");
        for(int i=0;i<8;i++) {
            worker.runOnce();
            clock.time.set(jdbc.queryForObject("SELECT next_attempt_at FROM bookings_mail_task WHERE booking_id=? AND event_type=?",(rs,row)->rs.getObject(1,LocalDateTime.class).toInstant(ZoneOffset.UTC),f.bookingId(),BookingMailQueue.COACH_LESSON_REMINDER));
        }
        assertThat(state(f,BookingMailQueue.STUDENT_LESSON_REMINDER)).isEqualTo("SKIPPED");
        assertThat(state(f,BookingMailQueue.COACH_LESSON_REMINDER)).isEqualTo("DEAD");
        assertThat(bookings.findById(f.bookingId()).status()).isEqualTo("CONFIRMED");
    }
    @Test void concurrentClaimsAndExpiredLeaseKeepOneOwnerAndRejectStaleWrites() throws Exception {
        var f=booking("CONFIRMED"); plan(f); clock.time.set(due(f));
        jdbc.update("DELETE FROM bookings_mail_task WHERE event_type=?",BookingMailQueue.COACH_LESSON_REMINDER);
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await();return queue.claim(clock.instant());});
            var b=pool.submit(()->{gate.await();return queue.claim(clock.instant());}); gate.countDown();
            var left=a.get(10,TimeUnit.SECONDS); var right=b.get(10,TimeUnit.SECONDS);
            assertThat(java.util.stream.Stream.of(left,right).filter(Objects::nonNull)).hasSize(1);
            var old=left==null?right:left; assertThat(old.claimUntil()).isEqualTo(clock.instant().plusSeconds(120));
            clock.time.set(old.claimUntil()); var renewed=queue.claim(clock.instant());
            assertThat(queue.ownsLease(old,clock.instant())).isFalse();
            queue.sent(old,clock.instant()); queue.skipped(old); queue.failed(old,clock.instant(),"stale",true);
            assertThat(state(f,BookingMailQueue.STUDENT_LESSON_REMINDER)).isEqualTo("CLAIMED");
            queue.sent(renewed,clock.instant()); assertThat(state(f,BookingMailQueue.STUDENT_LESSON_REMINDER)).isEqualTo("SENT");
        }
    }
    @Test void cancellationDuringContactLookupIsCheckedAgainBeforeSmtp() {
        var f=booking("CONFIRMED"); plan(f); clock.time.set(due(f));
        BookingMailContacts changed=id->{jdbc.update("UPDATE bookings_request SET status='CANCELLED_BY_STUDENT' WHERE id=?",f.bookingId());return contacts.emailForAccount(id);};
        new BookingMailWorker(queue,bookings,changed,sender,clock,links,new BookingReminderSettings(true)).runOnce();
        assertThat(sender.messages).isEmpty(); assertThat(state(f,BookingMailQueue.STUDENT_LESSON_REMINDER)).isEqualTo("SKIPPED");
    }
    @Test void pausesRemindersOnlyAndResumesPersistedWorkWithoutAffectingConfirmationMail() {
        var f=booking("CONFIRMED"); plan(f); clock.time.set(due(f));
        queue.enqueue(f.bookingId(),BookingMailQueue.BOOKING_CONFIRMED,f.studentId(),clock.instant());
        var paused=new BookingMailWorker(queue,bookings,contacts,sender,clock,links,new BookingReminderSettings(false));
        paused.runOnce(); assertThat(sender.messages).hasSize(1); assertThat(state(f,BookingMailQueue.STUDENT_LESSON_REMINDER)).isEqualTo("PENDING");
        var fresh=booking("CONFIRMED");
        new BookingReminderService(bookings,queue,new BookingReminderSettings(false),clock).scheduleConfirmed(fresh.bookingId(),clock.instant());
        assertThat(count()).isEqualTo(3);
        worker.runOnce(); assertThat(sender.messages).hasSize(3);
    }
    @Test void backfillsInBoundedBatchesAndNeverRecreatesCleanedCompletedTasks() {
        for(int i=0;i<53;i++) booking("CONFIRMED");
        reminders.backfill(); assertThat(count()).isEqualTo(100); reminders.backfill(); assertThat(count()).isEqualTo(106);
        reminders.backfill(); assertThat(count()).isEqualTo(106);
        jdbc.update("UPDATE bookings_mail_task SET status='SENT',created_at=?",utc(clock.instant().minusSeconds(8*86400L)));
        worker.cleanup(); assertThat(count()).isZero(); reminders.backfill(); assertThat(count()).isZero();
    }
    @Test void lateConfirmationsAndMissingConfirmationTimeDoNotInventReminders() {
        var late=booking("CONFIRMED"); clock.time.set(due(late).plusSeconds(1));
        jdbc.update("UPDATE bookings_request SET decided_at=? WHERE id=?",utc(clock.instant()),late.bookingId());
        var missing=booking("CONFIRMED"); jdbc.update("UPDATE bookings_request SET decided_at=NULL WHERE id=?",missing.bookingId());
        reminders.backfill(); assertThat(count()).isZero();
    }
    @Test void planningFailureRollsBackBothRecipientsAndTheDurableMarker() {
        var f=booking("CONFIRMED");
        jdbc.execute("ALTER TABLE bookings_mail_task ADD CONSTRAINT ck_reminder_test_fail CHECK (event_type<>'COACH_LESSON_REMINDER')");
        try { assertThatThrownBy(()->plan(f)).isInstanceOf(RuntimeException.class);
            assertThat(count()).isZero(); assertThat(jdbc.queryForObject("SELECT lesson_reminder_planned_at FROM bookings_request WHERE id=?",Object.class,f.bookingId())).isNull();
        } finally { jdbc.execute("ALTER TABLE bookings_mail_task DROP CHECK ck_reminder_test_fail"); }
    }
    @Test void backfillAndCancellationCompeteWithoutLeavingAnEffectiveCancelledReminder() throws Exception {
        var f=booking("CONFIRMED"); var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await(); reminders.backfill();return true;});
            var b=pool.submit(()->{gate.await();jdbc.update("UPDATE bookings_request SET status='CANCELLED_BY_STUDENT' WHERE id=?",f.bookingId());return true;});
            gate.countDown(); a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        clock.time.set(due(f));worker.runOnce(); assertThat(sender.messages).isEmpty(); assertThat(count()).isIn(0L,2L);
    }
    @Test void smtpSuccessFollowedByWritebackFailureCanReplayOnlyTheAffectedMessage() {
        var f=booking("CONFIRMED");plan(f);clock.time.set(due(f));
        var failed=new java.util.concurrent.atomic.AtomicBoolean();
        var interrupted=(BookingMailQueue)java.lang.reflect.Proxy.newProxyInstance(BookingMailQueue.class.getClassLoader(),
                new Class<?>[]{BookingMailQueue.class},(proxy,method,args)->{
                    if(method.getName().equals("sent") && ((BookingMailQueue.Task)args[0]).eventType().equals(BookingMailQueue.STUDENT_LESSON_REMINDER)
                            && failed.compareAndSet(false,true)) throw new IllegalStateException("fake writeback failure after SMTP");
                    try { return method.invoke(queue,args); } catch(java.lang.reflect.InvocationTargetException exception) { throw exception.getCause(); }
                });
        new BookingMailWorker(interrupted,bookings,contacts,sender,clock,links,new BookingReminderSettings(true)).runOnce();
        assertThat(sender.messages).hasSize(2);assertThat(state(f,BookingMailQueue.STUDENT_LESSON_REMINDER)).isEqualTo("PENDING");
        clock.time.set(clock.instant().plusSeconds(60));worker.runOnce();
        assertThat(sender.messages.stream().filter(m->m.email().startsWith(f.studentId())).count()).isEqualTo(2);
        assertThat(sender.messages.stream().filter(m->m.email().startsWith(f.coachId())).count()).isEqualTo(1);
    }
    @Test void concurrentBackfillersDoNotDuplicateTheTwoRecipientEvents() throws Exception {
        var f=booking("CONFIRMED");var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{gate.await();reminders.backfill();return true;});
            var b=pool.submit(()->{gate.await();reminders.backfill();return true;});gate.countDown();
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertThat(count()).isEqualTo(2);assertThat(bookings.findById(f.bookingId()).status()).isEqualTo("CONFIRMED");
    }
    @Test void smtpTransportActuallyDeliversBothMessagesToAnIsolatedMailpit() throws Exception {
        try(var mailpit=new GenericContainer<>("ghcr.io/axllent/mailpit:v1.31.3").withExposedPorts(1025,8025)) {
            mailpit.start(); var mail=new JavaMailSenderImpl();mail.setHost(mailpit.getHost());mail.setPort(mailpit.getMappedPort(1025));
            var factory=new StaticListableBeanFactory(); factory.addBean("mail",mail);
            sender.delegate=new SmtpBookingMailSender(factory.getBeanProvider(JavaMailSender.class),new MockEnvironment().withProperty("identity.mail.from","geer@example.test"));
            var f=booking("CONFIRMED");plan(f);clock.time.set(due(f));worker.runOnce();
            var client=HttpClient.newHttpClient();
            String json=client.send(HttpRequest.newBuilder(URI.create("http://"+mailpit.getHost()+":"+mailpit.getMappedPort(8025)+"/api/v1/messages")).GET().build(),HttpResponse.BodyHandlers.ofString()).body();
            assertThat((Integer)JsonPath.read(json,"$.total")).isEqualTo(2);
            List<String> ids=JsonPath.read(json,"$.messages[*].ID");
            for(String id:ids) {
                String message=client.send(HttpRequest.newBuilder(URI.create("http://"+mailpit.getHost()+":"+mailpit.getMappedPort(8025)+"/api/v1/message/"+id)).GET().build(),HttpResponse.BodyHandlers.ofString()).body();
                assertThat((String)JsonPath.read(message,"$.Text")).contains("距开课不足 24 小时无法取消预约","America/Toronto","https://lessons.example.test/#/");
            }
            assertThat(state(f,BookingMailQueue.STUDENT_LESSON_REMINDER)).isEqualTo("SENT");
            assertThat(state(f,BookingMailQueue.COACH_LESSON_REMINDER)).isEqualTo("SENT");
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
