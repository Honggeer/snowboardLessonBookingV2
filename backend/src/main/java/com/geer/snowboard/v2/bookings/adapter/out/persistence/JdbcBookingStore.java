package com.geer.snowboard.v2.bookings.adapter.out.persistence;

import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Booking;
import com.geer.snowboard.v2.bookings.application.port.out.BookingStore;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBookingStore implements BookingStore {
    private static final RowMapper<Booking> MAPPER = (rs, row) -> new Booking(
            rs.getString("id"), rs.getString("slot_id"), rs.getString("course_id"),
            rs.getString("coach_id"), rs.getString("student_id"), rs.getString("student_name_snapshot"),
            rs.getString("status"), rs.getString("decision_reason"), rs.getString("course_title_snapshot"),
            rs.getBigDecimal("price_amount_snapshot"), rs.getString("currency_snapshot"),
            rs.getString("mountain_id"), rs.getString("location_snapshot"), rs.getString("zone_id_snapshot"),
            rs.getObject("local_date_snapshot", LocalDate.class), instant(rs, "start_at_utc_snapshot"),
            instant(rs, "end_at_utc_snapshot"), instant(rs, "created_at"), nullableInstant(rs, "decided_at"));
    private final JdbcTemplate jdbc;
    public JdbcBookingStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Booking findById(String id) {
        return jdbc.query("SELECT * FROM bookings_request WHERE id=?", MAPPER, id).stream().findFirst().orElse(null);
    }
    @Override public List<Booking> lockUnplannedReminders(Instant now, int limit) {
        return jdbc.query("""
                SELECT * FROM bookings_request
                WHERE status='CONFIRMED' AND lesson_reminder_planned_at IS NULL
                  AND decided_at IS NOT NULL AND start_at_utc_snapshot>?
                ORDER BY start_at_utc_snapshot,id LIMIT ? FOR UPDATE SKIP LOCKED
                """, MAPPER, utc(now), limit);
    }
    @Override public boolean markReminderPlanned(String bookingId, Instant now) {
        return jdbc.update("""
                UPDATE bookings_request SET lesson_reminder_planned_at=?
                WHERE id=? AND status='CONFIRMED' AND lesson_reminder_planned_at IS NULL
                """, utc(now), bookingId)==1;
    }
    @Override public Booking lockById(String id) {
        return jdbc.query("SELECT * FROM bookings_request WHERE id=? FOR UPDATE", MAPPER, id)
                .stream().findFirst().orElse(null);
    }
    @Override public Booking findByKey(String studentId, String key) {
        return jdbc.query("SELECT * FROM bookings_request WHERE student_id=? AND idempotency_key=?",
                MAPPER, studentId, key).stream().findFirst().orElse(null);
    }
    @Override public String fingerprint(String studentId, String key) {
        return jdbc.query("SELECT request_fingerprint FROM bookings_request WHERE student_id=? AND idempotency_key=?",
                rs -> rs.next() ? rs.getString(1) : null, studentId, key);
    }
    @Override public Booking findByStudentSlot(String studentId, String slotId) {
        return jdbc.query("SELECT * FROM bookings_request WHERE student_id=? AND slot_id=? AND status<>'CANCELLED_BY_STUDENT'",
                MAPPER, studentId, slotId).stream().findFirst().orElse(null);
    }
    @Override public boolean insert(Booking booking, String key, String fingerprint) {
        return jdbc.update("""
                INSERT IGNORE INTO bookings_request
                (id,slot_id,course_id,coach_id,student_id,student_name_snapshot,status,course_title_snapshot,
                price_amount_snapshot,currency_snapshot,mountain_id,location_snapshot,zone_id_snapshot,local_date_snapshot,
                start_at_utc_snapshot,end_at_utc_snapshot,idempotency_key,request_fingerprint,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, booking.id(), booking.slotId(), booking.courseId(), booking.coachId(), booking.studentId(),
                booking.studentName(), booking.status(), booking.courseTitle(), booking.priceAmount(),
                booking.currency(), booking.mountainId(), booking.location(), booking.zoneId(), booking.localDate(),
                utc(booking.startAt()), utc(booking.endAt()), key, fingerprint, utc(booking.createdAt())) == 1;
    }
    @Override public Page<Booking> mine(String studentId, int limit, String cursor) {
        return page("student_id", studentId, null, limit, cursor);
    }
    @Override public Page<Booking> coach(String coachId, String status, int limit, String cursor) {
        return page("coach_id", coachId, status, limit, cursor);
    }
    private Page<Booking> page(String scopeColumn, String actorId, String status, int limit, String cursor) {
        String base = "SELECT * FROM bookings_request WHERE " + scopeColumn + "=? AND (? IS NULL OR status=?)";
        List<Booking> rows;
        if (cursor == null) {
            rows = jdbc.query(base + " ORDER BY created_at DESC,id DESC LIMIT ?", MAPPER,
                    actorId, status, status, limit + 1);
        } else {
            Booking previous = jdbc.query("SELECT * FROM bookings_request WHERE id=? AND " + scopeColumn + "=?",
                    MAPPER, cursor, actorId).stream().findFirst().orElse(null);
            if (previous == null) throw new BusinessProblem(400, "无效分页游标");
            rows = jdbc.query(base + " AND (created_at<? OR (created_at=? AND id<?)) ORDER BY created_at DESC,id DESC LIMIT ?",
                    MAPPER, actorId, status, status, utc(previous.createdAt()), utc(previous.createdAt()),
                    previous.id(), limit + 1);
        }
        return new Page<>(rows.subList(0, Math.min(limit, rows.size())),
                rows.size() > limit ? rows.get(limit - 1).id() : null);
    }
    @Override public void lockStudent(String studentId) {
        jdbc.update("INSERT IGNORE INTO bookings_student_guard (student_id) VALUES (?)", studentId);
        jdbc.query("SELECT student_id FROM bookings_student_guard WHERE student_id=? FOR UPDATE", rs -> null, studentId);
    }
    @Override public boolean confirmedOverlap(String studentId, Instant start, Instant end) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM bookings_request WHERE student_id=? AND status='CONFIRMED'
                AND start_at_utc_snapshot<? AND end_at_utc_snapshot>?)
                """, Boolean.class, studentId, utc(end), utc(start)));
    }
    @Override public boolean confirm(String id, Instant now) {
        return jdbc.update("UPDATE bookings_request SET status='CONFIRMED',decided_at=? WHERE id=? AND status='PENDING'",
                utc(now), id) == 1;
    }
    @Override public boolean reject(String id, String reason, Instant now) {
        return jdbc.update("UPDATE bookings_request SET status='REJECTED',decision_reason=?,decided_at=? WHERE id=? AND status='PENDING'",
                reason, utc(now), id) == 1;
    }
    @Override public boolean cancel(String id, String reason, Instant now) {
        return jdbc.update("""
                UPDATE bookings_request SET status='CANCELLED_BY_STUDENT',decision_reason=?,decided_at=?
                WHERE id=? AND status IN ('PENDING','CONFIRMED')
                """, reason, utc(now), id) == 1;
    }
    @Override public boolean hasConfirmedDay(String coachId, LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM bookings_request
                WHERE coach_id=? AND local_date_snapshot=? AND status='CONFIRMED')
                """, Boolean.class, coachId, date));
    }
    @Override public void rejectOtherPending(String slotId, String chosenId, Instant now) {
        jdbc.update("""
                UPDATE bookings_request SET status='REJECTED',decision_reason='该时段已确认给其他学员',decided_at=?
                WHERE slot_id=? AND id<>? AND status='PENDING'
                """, utc(now), slotId, chosenId);
    }
    @Override public void rejectOtherMountains(String coachId, LocalDate date, String mountainId, Instant now) {
        jdbc.update("""
                UPDATE bookings_request
                SET status='REJECTED',decision_reason='当天已确认在其他雪场授课',decided_at=?
                WHERE coach_id=? AND local_date_snapshot=? AND status='PENDING'
                  AND mountain_id IS NOT NULL AND mountain_id<>?
                """, utc(now), coachId, date, mountainId);
    }
    @Override public boolean hasPendingMountain(String mountainId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM bookings_request WHERE mountain_id=? AND status='PENDING')
                """, Boolean.class, mountainId));
    }
    @Override public boolean hasPendingDay(String coachId, LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM bookings_request
                WHERE coach_id=? AND local_date_snapshot=? AND status='PENDING')
                """, Boolean.class, coachId, date));
    }
    private static LocalDateTime utc(Instant point) { return LocalDateTime.ofInstant(point, ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        return rs.getObject(name, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
    private static Instant nullableInstant(ResultSet rs, String name) throws SQLException {
        LocalDateTime value = rs.getObject(name, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
