package com.geer.snowboard.v2.bookings.application.service;

import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations;
import com.geer.snowboard.v2.bookings.application.port.in.BookingReminderOperations;
import com.geer.snowboard.v2.bookings.application.port.out.BookedCourseLookup;
import com.geer.snowboard.v2.bookings.application.port.out.BookedSlotAccess;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailQueue;
import com.geer.snowboard.v2.bookings.application.port.out.BookingStore;
import com.geer.snowboard.v2.bookings.application.port.out.BookingStudentContacts;
import com.geer.snowboard.v2.bookings.domain.BookingStatus;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import com.geer.snowboard.v2.sharedkernel.Creation;
import com.geer.snowboard.v2.sharedkernel.Page;
import com.geer.snowboard.v2.sharedkernel.RequestKeys;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService implements BookingOperations {
    private final BookingStore store;
    private final BookedSlotAccess slots;
    private final BookedCourseLookup courses;
    private final BookingMailQueue mailQueue;
    private final Clock clock;
    private final BookingStudentContacts contacts;
    private final BookingReminderOperations reminders;
    public BookingService(BookingStore store, BookedSlotAccess slots, BookedCourseLookup courses,
                          BookingMailQueue mailQueue, Clock clock, BookingStudentContacts contacts,
                          BookingReminderOperations reminders) {
        this.store = store; this.slots = slots; this.courses = courses;
        this.mailQueue = mailQueue; this.clock = clock; this.contacts = contacts;
        this.reminders = reminders;
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Creation<Booking> apply(Actor actor, Apply command, String idempotencyKey) {
        actor.require("STUDENT");
        if (command == null || command.slotId() == null || command.slotId().isBlank()
                || command.courseId() == null || command.courseId().isBlank()
                || command.mountainId() == null || command.mountainId().isBlank())
            throw new BusinessProblem(400, "请选择课程、时段和雪场");
        String key = RequestKeys.key(idempotencyKey);
        String fingerprint = RequestKeys.fingerprint(command.courseId() + "\u0000" + command.slotId()
                + "\u0000" + command.mountainId());
        Booking existing = store.findByKey(actor.id(), key);
        if (existing != null) return replay(existing, store.fingerprint(actor.id(), key), fingerprint);
        String phone = contacts.currentPhone(actor);
        if (phone == null || phone.isBlank())
            throw new BusinessProblem(400, "请先填写联系电话，用于教练联系并确认预约");
        var preview = slots.find(command.slotId());
        if (preview == null) throw new BusinessProblem(404, "时段不存在");
        var day = slots.lockDay(preview.coachId(), preview.localDate());
        // A concurrent request may have committed while this request waited for the day lock.
        existing = store.findByKey(actor.id(), key);
        if (existing != null) return replay(existing, store.fingerprint(actor.id(), key), fingerprint);
        if (day == null || day.legacyReviewRequired()) throw new BusinessProblem(409, "当天地点待确认，暂不能申请");
        var slot = slots.lock(command.slotId());
        if (slot == null) throw new BusinessProblem(404, "时段不存在");
        if (!"OPEN".equals(slot.status()) || !slot.startAt().isAfter(clock.instant()))
            throw new BusinessProblem(409, "时段已不可申请");
        var mountain = slots.lockMountain(slot.coachId(), command.mountainId());
        if (mountain == null || !mountain.active()) throw new BusinessProblem(409, "雪场已不可申请");
        if (day.limitedMountainId() != null && !day.limitedMountainId().equals(mountain.id()))
            throw new BusinessProblem(409, "当天仅开放其他雪场");
        if (day.lockedMountainId() != null && !day.lockedMountainId().equals(mountain.id()))
            throw new BusinessProblem(409, "当天已锁定其他雪场");
        var course = courses.find(command.courseId());
        if (course == null || !course.coachId().equals(slot.coachId()))
            throw new BusinessProblem(409, "课程已不可申请");
        if (store.findByStudentSlot(actor.id(), slot.id()) != null)
            throw new BusinessProblem(409, "你已申请过这个时段，请查看我的预约");
        Instant now = clock.instant();
        Booking booking = new Booking(UUID.randomUUID().toString(), slot.id(), course.id(),
                slot.coachId(), actor.id(), actor.name(), "PENDING", null, course.title(),
                course.priceAmount(), course.currency(), mountain.id(), mountain.name(), slot.zoneId(), slot.localDate(),
                slot.startAt(), slot.endAt(), now, null);
        if (store.insert(booking, key, fingerprint)) {
            mailQueue.enqueue(booking.id(), BookingMailQueue.APPLICATION_RECEIVED, booking.coachId(), now);
            return new Creation<>(booking, true);
        }
        existing = store.findByKey(actor.id(), key);
        if (existing != null) return replay(existing, store.fingerprint(actor.id(), key), fingerprint);
        throw new BusinessProblem(409, "你已申请过这个时段，请查看我的预约");
    }

    @Override public Page<Booking> mine(Actor actor, Integer limit, String cursor) {
        actor.require("STUDENT");
        return store.mine(actor.id(), RequestKeys.limit(limit), cursor);
    }
    @Override public Page<CoachBooking> coach(Actor actor, String status, Integer limit, String cursor) {
        actor.require("COACH");
        if (status != null) {
            try { BookingStatus.valueOf(status); }
            catch (IllegalArgumentException error) { throw new BusinessProblem(400, "无效申请状态"); }
        }
        var page = store.coach(actor.id(), status, RequestKeys.limit(limit), cursor);
        var ids = page.items().stream().map(Booking::studentId).collect(Collectors.toSet());
        var phones = ids.isEmpty() ? Map.<String, String>of() : contacts.phonesForCoach(actor, ids);
        return new Page<>(page.items().stream().map(booking -> new CoachBooking(booking, phones.get(booking.studentId()))).toList(), page.nextCursor());
    }

    @Override public Booking mineOne(Actor actor, String bookingId) {
        actor.require("STUDENT");
        Booking booking = store.findById(bookingId);
        if (booking == null || !actor.id().equals(booking.studentId()))
            throw new BusinessProblem(404, "预约不存在");
        return booking;
    }

    @Override public CoachBooking coachOne(Actor actor, String bookingId) {
        actor.require("COACH");
        Booking booking = own(actor, bookingId);
        var phones = contacts.phonesForCoach(actor, Set.of(booking.studentId()));
        return new CoachBooking(booking, phones.get(booking.studentId()));
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking confirm(Actor actor, String bookingId) {
        actor.require("COACH");
        Booking candidate = own(actor, bookingId);
        var day = slots.lockDay(actor.id(), candidate.localDate());
        if (day == null || day.legacyReviewRequired()) throw new BusinessProblem(409, "当天旧地点待映射，暂不能确认");
        var slot = slots.lock(candidate.slotId());
        if (slot == null) throw new BusinessProblem(404, "时段不存在");
        store.lockStudent(candidate.studentId());
        candidate = store.lockById(bookingId);
        if (candidate == null || !actor.id().equals(candidate.coachId())) throw new BusinessProblem(404, "申请不存在");
        if (BookingStatus.valueOf(candidate.status()) == BookingStatus.CONFIRMED) return candidate;
        if (!BookingStatus.valueOf(candidate.status()).mayConfirm() || !"OPEN".equals(slot.status()))
            throw new BusinessProblem(409, "申请或时段已处理");
        if (candidate.mountainId() == null) throw new BusinessProblem(409, "旧申请地点待映射");
        if (day.limitedMountainId() != null && !day.limitedMountainId().equals(candidate.mountainId()))
            throw new BusinessProblem(409, "当天仅开放其他雪场");
        if (day.lockedMountainId() != null && !day.lockedMountainId().equals(candidate.mountainId()))
            throw new BusinessProblem(409, "当天已锁定其他雪场");
        if (store.confirmedOverlap(candidate.studentId(), candidate.startAt(), candidate.endAt()))
            throw new BusinessProblem(409, "学员已有重叠的已确认课程");
        slots.lockDayToMountain(actor.id(), candidate.localDate(), candidate.mountainId());
        slots.markBooked(slot.id());
        Instant now = clock.instant();
        if (!store.confirm(candidate.id(), now)) throw new BusinessProblem(409, "申请已被处理");
        store.rejectOtherPending(slot.id(), candidate.id(), now);
        store.rejectOtherMountains(actor.id(), candidate.localDate(), candidate.mountainId(), now);
        mailQueue.enqueue(candidate.id(), BookingMailQueue.BOOKING_CONFIRMED, candidate.studentId(), now);
        reminders.scheduleConfirmed(candidate.id(), now);
        return store.findById(candidate.id());
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking reject(Actor actor, String bookingId, Reject command) {
        actor.require("COACH");
        if (command == null || command.reason() == null || command.reason().isBlank())
            throw new BusinessProblem(400, "请填写拒绝原因");
        String reason = command.reason().strip();
        if (reason.length() > 200) throw new BusinessProblem(400, "拒绝原因过长");
        Booking candidate = own(actor, bookingId);
        slots.lockDay(actor.id(), candidate.localDate());
        var slot = slots.lock(candidate.slotId());
        if (slot == null) throw new BusinessProblem(404, "时段不存在");
        candidate = store.lockById(bookingId);
        if (candidate == null || !actor.id().equals(candidate.coachId())) throw new BusinessProblem(404, "申请不存在");
        if (BookingStatus.valueOf(candidate.status()) == BookingStatus.REJECTED) return candidate;
        if (!BookingStatus.valueOf(candidate.status()).mayReject())
            throw new BusinessProblem(409, "已确认申请不能拒绝");
        if (!store.reject(candidate.id(), reason, clock.instant())) throw new BusinessProblem(409, "申请已被处理");
        return store.findById(candidate.id());
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking cancel(Actor actor, String bookingId, Cancel command) {
        actor.require("STUDENT");
        String reason = command == null || command.reason() == null || command.reason().isBlank()
                ? null : command.reason().strip();
        if (reason != null && reason.length() > 200) throw new BusinessProblem(400, "取消原因过长");
        Booking candidate = store.findById(bookingId);
        if (candidate == null || !actor.id().equals(candidate.studentId()))
            throw new BusinessProblem(404, "预约不存在");
        slots.lockDay(candidate.coachId(), candidate.localDate());
        var slot = slots.lock(candidate.slotId());
        if (slot == null) throw new BusinessProblem(409, "预约时段不存在");
        store.lockStudent(actor.id());
        candidate = store.lockById(bookingId);
        if (candidate == null || !actor.id().equals(candidate.studentId()))
            throw new BusinessProblem(404, "预约不存在");
        BookingStatus status = BookingStatus.valueOf(candidate.status());
        if (status == BookingStatus.CANCELLED_BY_STUDENT) return candidate;
        if (status != BookingStatus.PENDING && status != BookingStatus.CONFIRMED)
            throw new BusinessProblem(409, "当前预约不能取消");
        Instant now = clock.instant();
        if (status == BookingStatus.CONFIRMED) {
            if (candidate.startAt().isBefore(now.plusSeconds(24 * 60 * 60)))
                throw new BusinessProblem(409, "距开课不足 24 小时，无法取消已确认预约");
            if (!"BOOKED".equals(slot.status())) throw new BusinessProblem(409, "已确认时段状态异常");
        }
        if (!store.cancel(candidate.id(), status == BookingStatus.PENDING ? null : reason, now))
            throw new BusinessProblem(409, "预约状态已变化，请刷新");
        if (status == BookingStatus.CONFIRMED) {
            slots.reopenBooked(slot.id());
            if (!store.hasConfirmedDay(candidate.coachId(), candidate.localDate()))
                slots.unlockDayMountain(candidate.coachId(), candidate.localDate());
        }
        return store.findById(candidate.id());
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public MountainResult deactivateMountain(Actor actor, String mountainId) {
        actor.require("COACH");
        var mountain = slots.lockMountain(actor.id(), mountainId);
        if (mountain == null) throw new BusinessProblem(404, "雪场不存在");
        if (mountain.active() && store.hasPendingMountain(mountainId))
            throw new BusinessProblem(409, "该雪场仍有待确认申请，请先处理");
        var result = mountain.active() ? slots.deactivateMountain(actor.id(), mountainId) : mountain;
        return new MountainResult(result.id(), result.name(), result.active());
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Creation<AvailabilityBatch> replaceAvailability(Actor actor, ReplacementRequest command, String idempotencyKey) {
        actor.require("COACH");
        var request = new BookedSlotAccess.AvailabilityRequest(command == null || command.days() == null ? null
                : command.days().stream().map(day -> day == null ? null :
                new BookedSlotAccess.AvailabilityDay(day.localDate(), day.startTime(), day.endTime(), day.mountainId())).toList());
        var gate = slots.lockReplacement(actor, request, idempotencyKey);
        if (gate.replay() != null) return new Creation<>(availability(gate.replay().value()), false);
        List<LocalDate> blocked = gate.dates().stream().filter(date -> store.hasPendingDay(actor.id(), date)).toList();
        if (!blocked.isEmpty()) throw new BusinessProblem(409, "这些日期有待确认申请，暂不能覆盖：" + blocked);
        var result = slots.finishReplacement(actor, request, idempotencyKey);
        return new Creation<>(availability(result.value()), result.created());
    }

    private static AvailabilityBatch availability(BookedSlotAccess.AvailabilityBatch batch) {
        return new AvailabilityBatch(batch.slots().stream()
                .map(slot -> new AvailabilitySlot(slot.id(), slot.localDate(), slot.startAt(), slot.endAt(), slot.status()))
                .toList(), batch.tails().stream().map(tail ->
                new AvailabilityTail(tail.localDate(), tail.startTime(), tail.endTime())).toList());
    }

    private Booking own(Actor actor, String bookingId) {
        Booking candidate = store.findById(bookingId);
        if (candidate == null || !actor.id().equals(candidate.coachId()))
            throw new BusinessProblem(404, "申请不存在");
        return candidate;
    }
    private static Creation<Booking> replay(Booking booking, String actual, String expected) {
        if (!expected.equals(actual)) throw new BusinessProblem(409, "同一请求键对应了不同申请内容");
        return new Creation<>(booking, false);
    }
}
