package com.geer.snowboard.v2.bookings.application.service;

import com.geer.snowboard.v2.bookings.application.port.in.BookingMailOperations;
import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Booking;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailContacts;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailQueue;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailSender;
import com.geer.snowboard.v2.bookings.application.port.out.BookingLinkBase;
import com.geer.snowboard.v2.bookings.application.port.out.BookingStore;
import com.geer.snowboard.v2.bookings.application.port.out.BookingReminderSettings;
import com.geer.snowboard.v2.bookings.application.port.out.LessonSummaryStore;
import com.geer.snowboard.v2.bookings.application.port.out.LessonSummarySettings;
import com.geer.snowboard.v2.bookings.domain.LessonSummary;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class BookingMailWorker implements BookingMailOperations {
    private static final int MAX_ATTEMPTS = 8;
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final BookingMailQueue queue;
    private final BookingStore bookings;
    private final BookingMailContacts contacts;
    private final BookingMailSender sender;
    private final Clock clock;
    private final String publicUrl;
    private final BookingReminderSettings settings;
    private final LessonSummaryStore summaries;
    private final LessonSummarySettings summarySettings;

    public BookingMailWorker(BookingMailQueue queue, BookingStore bookings,
                             BookingMailContacts contacts, BookingMailSender sender,
                             Clock clock, BookingLinkBase links, BookingReminderSettings settings, LessonSummaryStore summaries, LessonSummarySettings summarySettings) {
        this.queue = queue;
        this.bookings = bookings;
        this.contacts = contacts;
        this.sender = sender;
        this.clock = clock;
        this.publicUrl = links.value();
        this.settings = settings; this.summaries=summaries; this.summarySettings=summarySettings;
    }

    @Override
    public void runOnce() {
        for (int index = 0; index < 10; index++) {
            BookingMailQueue.Task task = queue.claim(clock.instant(), settings.enabled(), summarySettings.remindersEnabled());
            if (task == null) break;
            Booking booking = bookings.findById(task.bookingId());
            if (booking == null || !current(task, booking, clock.instant())) {
                queue.skipped(task);
                continue;
            }
            try {
                String email = contacts.emailForAccount(task.recipientAccountId());
                if (email == null || email.isBlank()) throw new IllegalStateException("Recipient account has no email");
                if (task.reminder() || summaryMail(task)) {
                    booking = bookings.findById(task.bookingId());
                    if (booking == null || !current(task, booking, clock.instant())) { queue.skipped(task); continue; }
                    if (!queue.ownsLease(task, clock.instant().plusSeconds(15))) continue;
                }
                sender.send(email, subject(task), body(task, booking));
                queue.sent(task, clock.instant());
            } catch (Exception exception) {
                if (task.reminder() && !clock.instant().isBefore(task.reminderStartAt().minusSeconds(86400))) {
                    queue.skipped(task); continue;
                }
                long delay = Math.min(3600, 60L << Math.min(Math.max(task.attempts() - 1, 0), 6));
                queue.failed(task, clock.instant().plusSeconds(delay),
                        exception.getClass().getSimpleName(), task.attempts() >= MAX_ATTEMPTS);
            }
        }
    }

    @Override
    public void cleanup() {
        queue.cleanup(clock.instant().minusSeconds(7 * 86400));
    }

    private boolean current(BookingMailQueue.Task task, Booking booking, java.time.Instant now) {
        return switch (task.eventType()) {
            case BookingMailQueue.APPLICATION_RECEIVED ->
                    booking.status().equals("PENDING") && task.recipientAccountId().equals(booking.coachId());
            case BookingMailQueue.BOOKING_CONFIRMED ->
                    booking.status().equals("CONFIRMED") && task.recipientAccountId().equals(booking.studentId());
            case BookingMailQueue.BOOKING_REJECTED ->
                    booking.status().equals("REJECTED") && task.recipientAccountId().equals(booking.studentId());
            case BookingMailQueue.STUDENT_LESSON_REMINDER, BookingMailQueue.COACH_LESSON_REMINDER ->
                    booking.status().equals("CONFIRMED") && booking.startAt().equals(task.reminderStartAt())
                    && now.isBefore(booking.startAt().minusSeconds(86400))
                    && task.recipientAccountId().equals(task.eventType().equals(BookingMailQueue.STUDENT_LESSON_REMINDER)
                            ? booking.studentId() : booking.coachId());
            case BookingMailQueue.COACH_SUMMARY_REMINDER ->
                    booking.status().equals("CONFIRMED") && booking.endAt().equals(task.summaryEndAt())
                    && !now.isBefore(booking.endAt()) && task.recipientAccountId().equals(booking.coachId())
                    && !published(booking.id());
            case BookingMailQueue.STUDENT_SUMMARY_PUBLISHED ->
                    LessonSummary.completed(booking.status(),booking.endAt(),now)
                    && task.recipientAccountId().equals(booking.studentId()) && published(booking.id());
            default -> false;
        };
    }

    private boolean published(String bookingId) {
        LessonSummary summary=summaries.find(bookingId);
        return summary!=null && summary.state()==LessonSummary.State.PUBLISHED;
    }
    private static boolean summaryMail(BookingMailQueue.Task task) {
        return BookingMailQueue.COACH_SUMMARY_REMINDER.equals(task.eventType()) || BookingMailQueue.STUDENT_SUMMARY_PUBLISHED.equals(task.eventType());
    }
    private static String subject(BookingMailQueue.Task task) {
        if (task.eventType().equals(BookingMailQueue.COACH_SUMMARY_REMINDER)) return "GEER 课后提醒：请填写课程总结";
        if (task.eventType().equals(BookingMailQueue.STUDENT_SUMMARY_PUBLISHED)) return "GEER 课程总结已发布";
        if (task.reminder()) return "GEER 课前提醒：即将上课";
        if (task.eventType().equals(BookingMailQueue.BOOKING_REJECTED)) return "GEER 预约申请未通过";
        return task.eventType().equals(BookingMailQueue.APPLICATION_RECEIVED)
                ? "GEER 收到新的预约申请" : "GEER 课程预约已确认";
    }

    private String body(BookingMailQueue.Task task, Booking booking) {
        ZoneId zone = ZoneId.of(booking.zoneId());
        var formatter = task.reminder() || summaryMail(task) ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm xxx", Locale.ROOT) : DATE_TIME;
        String schedule = formatter.withZone(zone).format(booking.startAt())
                + " – " + formatter.withZone(zone).format(booking.endAt())
                + " (" + booking.zoneId() + ")";
        String details = "课程：" + booking.courseTitle() + "\n"
                + "雪场：" + booking.location() + "\n"
                + "时间：" + schedule + "\n";
        if (summaryMail(task)) {
            boolean coach=task.eventType().equals(BookingMailQueue.COACH_SUMMARY_REMINDER);
            return (coach ? "课程已结束，请填写本节练习内容、待加强和课后强化练习。\n学员："+booking.studentName()+"\n"
                    : "教练已发布你的课程总结和课后强化练习。\n") + details
                    + (coach ? "填写课程总结：" : "查看课程总结：") + publicUrl
                    + (coach ? "/#/coach-lesson-summaries/" : "/#/completed-lessons/") + booking.id()
                    + "\n\n请登录后查看，以页面最新内容为准。";
        }
        if (task.eventType().equals(BookingMailQueue.BOOKING_REJECTED)) {
            return "你的预约申请未通过。\n拒绝原因：" + booking.decisionReason() + "\n" + details
                    + "查看预约：" + publicUrl + "/#/my-bookings/" + booking.id()
                    + "\n\n请登录后查看，以页面当前状态为准。";
        }
        if (task.reminder()) {
            boolean coach = task.eventType().equals(BookingMailQueue.COACH_LESSON_REMINDER);
            String deadline = formatter.withZone(zone).format(booking.startAt().minusSeconds(86400));
            return (coach ? "你有一节即将开始的雪板课程。\n学员：" + booking.studentName() + "\n"
                    : "你的雪板课程即将开始，期待在雪场见到你！\n")
                    + details + "学员如需取消，请在 " + deadline + " (" + booking.zoneId() + ") 及之前操作。\n"
                    + "距开课不足 24 小时无法取消预约。\n"
                    + "查看预约：" + publicUrl + (coach ? "/#/coach-applications/" : "/#/my-bookings/") + booking.id()
                    + "\n\n请登录后查看，以页面当前状态为准。";
        }
        if (task.eventType().equals(BookingMailQueue.APPLICATION_RECEIVED)) {
            return "收到新的预约申请，等待你确认。\n学员：" + booking.studentName() + "\n"
                    + details + "查看申请：" + publicUrl + "/#/coach-applications/" + booking.id()
                    + "\n\n请登录后处理，以页面当前状态为准。";
        }
        return "你的课程预约已获教练确认。\n" + details
                + "已确认预约须在课程开始至少 24 小时前取消。\n"
                + "查看预约：" + publicUrl + "/#/my-bookings/" + booking.id()
                + "\n\n请登录后查看，以页面当前状态为准。";
    }
}
