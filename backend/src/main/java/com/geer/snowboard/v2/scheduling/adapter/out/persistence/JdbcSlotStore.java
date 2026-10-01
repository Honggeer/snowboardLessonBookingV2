package com.geer.snowboard.v2.scheduling.adapter.out.persistence;

import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.DayPolicy;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.Mountain;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.Slot;
import com.geer.snowboard.v2.scheduling.application.port.out.SlotStore;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSlotStore implements SlotStore {
    private static final RowMapper<Slot> SLOT_MAPPER = (rs, row) -> new Slot(
            rs.getString("id"), rs.getString("coach_id"), rs.getString("course_id"),
            rs.getString("location"), rs.getString("zone_id"), rs.getObject("local_date", LocalDate.class),
            instant(rs, "start_at_utc"), instant(rs, "end_at_utc"), rs.getString("status"), List.of());
    private static final RowMapper<Mountain> MOUNTAIN_MAPPER = (rs, row) -> new Mountain(
            rs.getString("id"), rs.getString("coach_id"), rs.getString("name"), rs.getBoolean("active"));
    private static final RowMapper<DayPolicy> DAY_MAPPER = (rs, row) -> new DayPolicy(
            rs.getString("coach_id"), rs.getObject("local_date", LocalDate.class),
            rs.getString("limited_mountain_id"), rs.getString("locked_mountain_id"),
            rs.getBoolean("legacy_review_required"));
    private final JdbcTemplate jdbc;
    public JdbcSlotStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Mountain mountainByKey(String coachId, String key) {
        return jdbc.query("SELECT * FROM scheduling_mountain WHERE coach_id=? AND idempotency_key=?",
                MOUNTAIN_MAPPER, coachId, key).stream().findFirst().orElse(null);
    }
    @Override public String mountainFingerprint(String coachId, String key) {
        return jdbc.query("SELECT request_fingerprint FROM scheduling_mountain WHERE coach_id=? AND idempotency_key=?",
                rs -> rs.next() ? rs.getString(1) : null, coachId, key);
    }
    @Override public boolean insertMountain(Mountain mountain, String key, String fingerprint) {
        return jdbc.update("INSERT IGNORE INTO scheduling_mountain (id,coach_id,name,active,idempotency_key,request_fingerprint,created_at) VALUES (?,?,?,?,?,?,?)",
                mountain.id(), mountain.coachId(), mountain.name(), mountain.active(), key, fingerprint,
                utc(Instant.now())) == 1;
    }
    @Override public Mountain mountain(String coachId, String id) {
        return jdbc.query("SELECT * FROM scheduling_mountain WHERE coach_id=? AND id=?", MOUNTAIN_MAPPER,
                coachId, id).stream().findFirst().orElse(null);
    }
    @Override public Mountain lockMountain(String coachId, String id) {
        return jdbc.query("SELECT * FROM scheduling_mountain WHERE coach_id=? AND id=? FOR UPDATE",
                MOUNTAIN_MAPPER, coachId, id).stream().findFirst().orElse(null);
    }
    @Override public Page<Mountain> mountains(String coachId, int limit, String cursor) {
        if (cursor != null && mountain(coachId, cursor) == null) throw new BusinessProblem(400, "无效分页游标");
        List<Mountain> rows = jdbc.query("SELECT * FROM scheduling_mountain WHERE coach_id=? AND (? IS NULL OR id<?) ORDER BY id DESC LIMIT ?",
                MOUNTAIN_MAPPER, coachId, cursor, cursor, limit + 1);
        return new Page<>(rows.subList(0, Math.min(limit, rows.size())),
                rows.size() > limit ? rows.get(limit - 1).id() : null);
    }
    @Override public List<Mountain> activeMountains(String coachId) {
        return jdbc.query("SELECT * FROM scheduling_mountain WHERE coach_id=? AND active=TRUE ORDER BY name,id",
                MOUNTAIN_MAPPER, coachId);
    }
    @Override public void renameMountain(String coachId, String id, String name) {
        try {
            jdbc.update("UPDATE scheduling_mountain SET name=? WHERE coach_id=? AND id=?", name, coachId, id);
        } catch (DuplicateKeyException error) {
            throw new BusinessProblem(409, "雪场名称已存在");
        }
    }
    @Override public void deactivateMountain(String coachId, String id) {
        jdbc.update("UPDATE scheduling_mountain SET active=FALSE WHERE coach_id=? AND id=?", coachId, id);
    }

    @Override public void lockCoach(String coachId) {
        jdbc.update("INSERT IGNORE INTO scheduling_coach_guard (coach_id) VALUES (?)", coachId);
        jdbc.query("SELECT coach_id FROM scheduling_coach_guard WHERE coach_id=? FOR UPDATE", rs -> null, coachId);
    }
    @Override public void ensureDay(String coachId, LocalDate date, String limitedMountainId) {
        jdbc.update("INSERT IGNORE INTO scheduling_day (coach_id,local_date,limited_mountain_id) VALUES (?,?,?)",
                coachId, date, limitedMountainId);
    }
    @Override public DayPolicy lockDay(String coachId, LocalDate date) {
        return jdbc.query("SELECT * FROM scheduling_day WHERE coach_id=? AND local_date=? FOR UPDATE",
                DAY_MAPPER, coachId, date).stream().findFirst().orElse(null);
    }
    @Override public DayPolicy day(String coachId, LocalDate date) {
        return jdbc.query("SELECT * FROM scheduling_day WHERE coach_id=? AND local_date=?",
                DAY_MAPPER, coachId, date).stream().findFirst().orElse(null);
    }
    @Override public void lockDayToMountain(String coachId, LocalDate date, String mountainId) {
        if (jdbc.update("""
                UPDATE scheduling_day SET locked_mountain_id=?
                WHERE coach_id=? AND local_date=? AND legacy_review_required=FALSE
                  AND (locked_mountain_id IS NULL OR locked_mountain_id=?)
                  AND (limited_mountain_id IS NULL OR limited_mountain_id=?)
                """, mountainId, coachId, date, mountainId, mountainId) != 1)
            throw new BusinessProblem(409, "当天已锁定其他雪场");
    }
    @Override public void replaceDayLimit(String coachId, LocalDate date, String mountainId) {
        jdbc.update("UPDATE scheduling_day SET limited_mountain_id=? WHERE coach_id=? AND local_date=?",
                mountainId, coachId, date);
    }
    @Override public List<DayPolicy> daysInMonth(String coachId, LocalDate first, LocalDate last) {
        return jdbc.query("SELECT * FROM scheduling_day WHERE coach_id=? AND local_date BETWEEN ? AND ? ORDER BY local_date",
                DAY_MAPPER, coachId, first, last);
    }
    @Override public List<Slot> slotsInMonth(String coachId, LocalDate first, LocalDate last) {
        return jdbc.query("""
                SELECT * FROM scheduling_slot WHERE coach_id=? AND local_date BETWEEN ? AND ?
                AND status IN ('OPEN','BOOKED') ORDER BY local_date,start_at_utc,id
                """, SLOT_MAPPER, coachId, first, last);
    }
    @Override public List<Slot> lockActiveDaySlots(String coachId, LocalDate date) {
        return jdbc.query("""
                SELECT * FROM scheduling_slot WHERE coach_id=? AND local_date=? AND status IN ('OPEN','BOOKED')
                ORDER BY start_at_utc,id FOR UPDATE
                """, SLOT_MAPPER, coachId, date);
    }
    @Override public void closeOpenDaySlots(String coachId, LocalDate date) {
        jdbc.update("UPDATE scheduling_slot SET status='CLOSED' WHERE coach_id=? AND local_date=? AND status='OPEN'",
                coachId, date);
    }
    @Override public boolean overlaps(String coachId, Instant start, Instant end) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM scheduling_slot WHERE coach_id=? AND status IN ('OPEN','BOOKED') AND start_at_utc<? AND end_at_utc>?)",
                Boolean.class, coachId, utc(end), utc(start)));
    }
    @Override public BatchRecord batchByKey(String coachId, String key) {
        return jdbc.query("SELECT id,request_fingerprint FROM scheduling_batch WHERE coach_id=? AND idempotency_key=?",
                (rs, row) -> new BatchRecord(rs.getString(1), rs.getString(2)), coachId, key)
                .stream().findFirst().orElse(null);
    }
    @Override public void insertBatch(String id, String coachId, String key, String fingerprint) {
        jdbc.update("INSERT INTO scheduling_batch (id,coach_id,idempotency_key,request_fingerprint,created_at) VALUES (?,?,?,?,?)",
                id, coachId, key, fingerprint, utc(Instant.now()));
    }
    @Override public void insertSlot(Slot slot, String batchId, String fingerprint) {
        jdbc.update("""
                INSERT INTO scheduling_slot
                (id,coach_id,course_id,location,zone_id,local_date,start_at_utc,end_at_utc,status,
                 idempotency_key,request_fingerprint,created_at,batch_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, slot.id(), slot.coachId(), null, null, slot.zoneId(), slot.localDate(),
                utc(slot.startAt()), utc(slot.endAt()), slot.status(), UUID.randomUUID().toString(),
                fingerprint, utc(Instant.now()), batchId);
    }
    @Override public List<Slot> batchSlots(String batchId) {
        return jdbc.query("SELECT * FROM scheduling_slot WHERE batch_id=? ORDER BY start_at_utc,id",
                SLOT_MAPPER, batchId);
    }
    @Override public Page<Slot> open(LocalDate from, LocalDate to, Instant now, int limit, String cursor) {
        String base = """
                SELECT s.* FROM scheduling_slot s
                JOIN scheduling_day d ON d.coach_id=s.coach_id AND d.local_date=s.local_date
                WHERE s.status='OPEN' AND s.local_date BETWEEN ? AND ? AND s.start_at_utc>?
                  AND d.legacy_review_required=FALSE
                  AND EXISTS (SELECT 1 FROM scheduling_mountain m
                    WHERE m.coach_id=s.coach_id AND m.active=TRUE
                      AND (d.limited_mountain_id IS NULL OR m.id=d.limited_mountain_id)
                      AND (d.locked_mountain_id IS NULL OR m.id=d.locked_mountain_id))
                """;
        List<Slot> rows;
        if (cursor == null) {
            rows = jdbc.query(base + " ORDER BY s.start_at_utc,s.id LIMIT ?", SLOT_MAPPER,
                    from, to, utc(now), limit + 1);
        } else {
            Slot previous = jdbc.query("SELECT * FROM scheduling_slot WHERE id=?", SLOT_MAPPER,
                    cursor).stream().findFirst().orElse(null);
            if (previous == null) throw new BusinessProblem(400, "无效分页游标");
            rows = jdbc.query(base + " AND (s.start_at_utc>? OR (s.start_at_utc=? AND s.id>?)) ORDER BY s.start_at_utc,s.id LIMIT ?",
                    SLOT_MAPPER, from, to, utc(now), utc(previous.startAt()), utc(previous.startAt()),
                    previous.id(), limit + 1);
        }
        return page(rows, limit);
    }
    @Override public Page<Slot> coachSlots(String coachId, int limit, String cursor) {
        List<Slot> rows = jdbc.query("SELECT * FROM scheduling_slot WHERE coach_id=? AND status IN ('OPEN','BOOKED') AND (? IS NULL OR id<?) ORDER BY id DESC LIMIT ?",
                SLOT_MAPPER, coachId, cursor, cursor, limit + 1);
        return page(rows, limit);
    }
    @Override public List<Mountain> availableMountains(String coachId, LocalDate date) {
        return jdbc.query("""
                SELECT m.* FROM scheduling_mountain m
                JOIN scheduling_day d ON d.coach_id=m.coach_id AND d.local_date=?
                WHERE m.coach_id=? AND m.active=TRUE AND d.legacy_review_required=FALSE
                  AND (d.limited_mountain_id IS NULL OR m.id=d.limited_mountain_id)
                  AND (d.locked_mountain_id IS NULL OR m.id=d.locked_mountain_id)
                ORDER BY m.name,m.id
                """, MOUNTAIN_MAPPER, date, coachId);
    }
    @Override public Slot find(String id) {
        return jdbc.query("SELECT * FROM scheduling_slot WHERE id=?", SLOT_MAPPER, id)
                .stream().findFirst().orElse(null);
    }
    @Override public Slot lock(String id) {
        return jdbc.query("SELECT * FROM scheduling_slot WHERE id=? FOR UPDATE", SLOT_MAPPER, id)
                .stream().findFirst().orElse(null);
    }
    @Override public boolean markBooked(String id) {
        return jdbc.update("UPDATE scheduling_slot SET status='BOOKED' WHERE id=? AND status='OPEN'", id) == 1;
    }
    @Override public boolean reopenBooked(String id) {
        return jdbc.update("UPDATE scheduling_slot SET status='OPEN' WHERE id=? AND status='BOOKED'", id) == 1;
    }
    @Override public void unlockDayMountain(String coachId, LocalDate date) {
        jdbc.update("UPDATE scheduling_day SET locked_mountain_id=NULL WHERE coach_id=? AND local_date=?",
                coachId, date);
    }
    private static Page<Slot> page(List<Slot> rows, int limit) {
        return new Page<>(rows.subList(0, Math.min(limit, rows.size())),
                rows.size() > limit ? rows.get(limit - 1).id() : null);
    }
    private static LocalDateTime utc(Instant point) { return LocalDateTime.ofInstant(point, ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        return rs.getObject(name, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
}
