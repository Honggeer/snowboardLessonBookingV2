package com.geer.snowboard.v2.bookings.application.service;

import com.geer.snowboard.v2.bookings.application.port.in.BookingMailOperations;
import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Booking;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailContacts;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailQueue;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailSender;
import com.geer.snowboard.v2.bookings.application.port.out.BookingLinkBase;
import com.geer.snowboard.v2.bookings.application.port.out.BookingStore;
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

    public BookingMailWorker(BookingMailQueue queue, BookingStore bookings,
                             BookingMailContacts contacts, BookingMailSender sender,
                             Clock clock, BookingLinkBase links) {
        this.queue = queue;
        this.bookings = bookings;
        this.contacts = contacts;
        this.sender = sender;
        this.clock = clock;
        this.publicUrl = links.value();
    }

    @Override
    public void runOnce() {
        for (int index = 0; index < 10; index++) {
            BookingMailQueue.Task task = queue.claim(clock.instant());
            if (task == null) break;
            Booking booking = bookings.findById(task.bookingId());
            if (booking == null || !current(task, booking)) {
                queue.skipped(task);
                continue;
            }
            try {
                String email = contacts.emailForAccount(task.recipientAccountId());
                if (email == null || email.isBlank()) throw new IllegalStateException("Recipient account has no email");
                sender.send(email, subject(task), body(task, booking));
                queue.sent(task, clock.instant());
            } catch (Exception exception) {
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

    private static boolean current(BookingMailQueue.Task task, Booking booking) {
        return switch (task.eventType()) {
            case BookingMailQueue.APPLICATION_RECEIVED ->
                    booking.status().equals("PENDING") && task.recipientAccountId().equals(booking.coachId());
            case BookingMailQueue.BOOKING_CONFIRMED ->
                    booking.status().equals("CONFIRMED") && task.recipientAccountId().equals(booking.studentId());
            default -> false;
        };
    }

    private static String subject(BookingMailQueue.Task task) {
        return task.eventType().equals(BookingMailQueue.APPLICATION_RECEIVED)
                ? "GEER 收到新的预约申请" : "GEER 课程预约已确认";
    }

    private String body(BookingMailQueue.Task task, Booking booking) {
        ZoneId zone = ZoneId.of(booking.zoneId());
        String schedule = DATE_TIME.withZone(zone).format(booking.startAt())
                + " – " + DATE_TIME.withZone(zone).format(booking.endAt())
                + " (" + booking.zoneId() + ")";
        String details = "课程：" + booking.courseTitle() + "\n"
                + "雪场：" + booking.location() + "\n"
                + "时间：" + schedule + "\n";
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
