package com.geer.snowboard.v2.scheduling.application.service;

import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations;
import com.geer.snowboard.v2.scheduling.application.port.out.SlotStore;
import com.geer.snowboard.v2.scheduling.domain.AvailabilityRange;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import com.geer.snowboard.v2.sharedkernel.Creation;
import com.geer.snowboard.v2.sharedkernel.Page;
import com.geer.snowboard.v2.sharedkernel.RequestKeys;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotService implements SlotOperations {
    private record PreparedDay(LocalDate date, LocalTime start, LocalTime end, String mountainId,
                               AvailabilityRange.Split split) {}

    private final SlotStore store;
    private final Clock clock;
    public SlotService(SlotStore store, Clock clock) { this.store = store; this.clock = clock; }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Creation<Mountain> createMountain(Actor actor, MountainName command, String idempotencyKey) {
        actor.require("COACH");
        String name = clean(command == null ? null : command.name(), 200, "雪场名称");
        String key = RequestKeys.key(idempotencyKey);
        String fingerprint = RequestKeys.fingerprint(name);
        Mountain existing = store.mountainByKey(actor.id(), key);
        if (existing != null) return mountainReplay(existing, store.mountainFingerprint(actor.id(), key), fingerprint);
        Mountain mountain = new Mountain(UUID.randomUUID().toString(), actor.id(), name, true);
        if (store.insertMountain(mountain, key, fingerprint)) return new Creation<>(mountain, true);
        existing = store.mountainByKey(actor.id(), key);
        if (existing != null) return mountainReplay(existing, store.mountainFingerprint(actor.id(), key), fingerprint);
        throw new BusinessProblem(409, "雪场名称已存在");
    }

    @Override public Page<Mountain> mountains(Actor actor, Integer limit, String cursor) {
        actor.require("COACH");
        return store.mountains(actor.id(), RequestKeys.limit(limit), cursor);
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Mountain renameMountain(Actor actor, String id, MountainName command) {
        actor.require("COACH");
        String name = clean(command == null ? null : command.name(), 200, "雪场名称");
        Mountain mountain = store.lockMountain(actor.id(), id);
        if (mountain == null) throw new BusinessProblem(404, "雪场不存在");
        if (!mountain.name().equals(name)) store.renameMountain(actor.id(), id, name);
        return store.mountain(actor.id(), id);
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public Creation<Batch> createBatch(Actor actor, BatchRequest command, String idempotencyKey) {
        actor.require("COACH");
        String key = RequestKeys.key(idempotencyKey);
        List<PreparedDay> days = prepare(command);
        StringBuilder canonical = new StringBuilder();
        for (PreparedDay day : days) canonical.append(day.date()).append('|').append(day.start())
                .append('|').append(day.end()).append('|').append(day.mountainId()).append(';');
        String fingerprint = RequestKeys.fingerprint(canonical.toString());
        List<Tail> tails = tails(days);
        SlotStore.BatchRecord prior = store.batchByKey(actor.id(), key);
        if (prior != null) return batchReplay(prior, fingerprint, tails);
        LocalDate today = LocalDate.now(clock.withZone(AvailabilityRange.ZONE));
        for (PreparedDay day : days) {
            if (day.date().isBefore(today))
                throw new BusinessProblem(400, "不能选择过去的日期");
        }
        if (store.activeMountains(actor.id()).isEmpty())
            throw new BusinessProblem(409, "请先设置至少一座雪场");
        store.lockCoach(actor.id());
        for (PreparedDay day : days) {
            if (day.mountainId() != null) {
                Mountain mountain = store.lockMountain(actor.id(), day.mountainId());
                if (mountain == null || !mountain.active()) throw new BusinessProblem(404, "雪场不存在或已停用");
            }
            store.ensureDay(actor.id(), day.date(), day.mountainId());
            DayPolicy policy = store.lockDay(actor.id(), day.date());
            if (policy.legacyReviewRequired()) throw new BusinessProblem(409, "当天旧地点待映射，暂不能发布");
            if (!Objects.equals(policy.limitedMountainId(), day.mountainId()))
                throw new BusinessProblem(409, "当天已使用另一种雪场限制");
            if (policy.lockedMountainId() != null && day.mountainId() != null
                    && !policy.lockedMountainId().equals(day.mountainId()))
                throw new BusinessProblem(409, "当天已锁定其他雪场");
            for (var window : day.split().windows()) {
                if (!window.start().isAfter(clock.instant())) throw new BusinessProblem(400, "只能发布未来时段");
                if (store.overlaps(actor.id(), window.start(), window.end()))
                    throw new BusinessProblem(409, "时段与已有排课重叠");
            }
        }
        String batchId = UUID.randomUUID().toString();
        store.insertBatch(batchId, actor.id(), key, fingerprint);
        for (PreparedDay day : days) {
            for (var window : day.split().windows()) {
                Slot slot = new Slot(UUID.randomUUID().toString(), actor.id(), null, null,
                        AvailabilityRange.ZONE.getId(), day.date(), window.start(), window.end(), "OPEN", List.of());
                store.insertSlot(slot, batchId, fingerprint);
            }
        }
        return new Creation<>(new Batch(store.batchSlots(batchId).stream().map(this::enrich).toList(), tails), true);
    }

    @Override public ReplacementLock lockReplacement(Actor actor, BatchRequest command, String idempotencyKey) {
        actor.require("COACH");
        String key = RequestKeys.key(idempotencyKey);
        List<PreparedDay> days = prepare(command);
        String fingerprint = replacementFingerprint(days);
        List<LocalDate> dates = days.stream().map(PreparedDay::date).toList();
        SlotStore.BatchRecord prior = store.batchByKey(actor.id(), key);
        if (prior != null) return new ReplacementLock(dates, batchReplay(prior, fingerprint, tails(days)));
        LocalDate today = LocalDate.now(clock.withZone(AvailabilityRange.ZONE));
        for (PreparedDay day : days) {
            if (day.date().isBefore(today))
                throw new BusinessProblem(400, "不能选择过去的日期");
            for (var window : day.split().windows()) {
                if (!window.start().isAfter(clock.instant())) throw new BusinessProblem(400, "只能发布未来时段");
            }
        }
        store.lockCoach(actor.id());
        prior = store.batchByKey(actor.id(), key);
        if (prior != null) return new ReplacementLock(dates, batchReplay(prior, fingerprint, tails(days)));
        if (store.activeMountains(actor.id()).isEmpty())
            throw new BusinessProblem(409, "请先设置至少一座雪场");
        for (PreparedDay day : days) {
            store.ensureDay(actor.id(), day.date(), null);
            DayPolicy policy = store.lockDay(actor.id(), day.date());
            if (policy.legacyReviewRequired()) throw new BusinessProblem(409, "当天旧地点待映射，暂不能覆盖");
            store.lockActiveDaySlots(actor.id(), day.date());
        }
        return new ReplacementLock(dates, null);
    }

    @Override public Creation<Batch> finishReplacement(Actor actor, BatchRequest command, String idempotencyKey) {
        ReplacementLock locked = lockReplacement(actor, command, idempotencyKey);
        if (locked.replay() != null) return locked.replay();
        List<PreparedDay> days = prepare(command);
        String key = RequestKeys.key(idempotencyKey);
        String fingerprint = replacementFingerprint(days);
        for (PreparedDay day : days) {
            DayPolicy policy = store.lockDay(actor.id(), day.date());
            if (day.mountainId() != null) {
                Mountain mountain = store.lockMountain(actor.id(), day.mountainId());
                if (mountain == null || !mountain.active()) throw new BusinessProblem(404, "雪场不存在或已停用");
            }
            if (policy.lockedMountainId() != null && day.mountainId() != null
                    && !policy.lockedMountainId().equals(day.mountainId()))
                throw new BusinessProblem(409, "当天已锁定其他雪场");
            store.closeOpenDaySlots(actor.id(), day.date());
            store.replaceDayLimit(actor.id(), day.date(), day.mountainId());
            for (var window : day.split().windows()) {
                if (store.overlaps(actor.id(), window.start(), window.end()))
                    throw new BusinessProblem(409, "新时段与已确认排课重叠");
            }
        }
        String batchId = UUID.randomUUID().toString();
        store.insertBatch(batchId, actor.id(), key, fingerprint);
        for (PreparedDay day : days) {
            for (var window : day.split().windows()) {
                Slot slot = new Slot(UUID.randomUUID().toString(), actor.id(), null, null,
                        AvailabilityRange.ZONE.getId(), day.date(), window.start(), window.end(), "OPEN", List.of());
                store.insertSlot(slot, batchId, fingerprint);
            }
        }
        return new Creation<>(new Batch(store.batchSlots(batchId).stream().map(this::enrich).toList(), tails(days)), true);
    }

    @Override public MonthSchedule month(Actor actor, Integer year, Integer month) {
        actor.require("COACH");
        LocalDate first;
        try {
            if (year == null || month == null || year < 2000 || year > 2100)
                throw new DateTimeException("invalid month");
            first = LocalDate.of(year, month, 1);
        } catch (DateTimeException error) {
            throw new BusinessProblem(400, "请提供有效月份");
        }
        LocalDate last = first.plusMonths(1).minusDays(1);
        Map<LocalDate, List<Slot>> byDate = store.slotsInMonth(actor.id(), first, last).stream()
                .collect(Collectors.groupingBy(Slot::localDate));
        List<MonthDay> result = store.daysInMonth(actor.id(), first, last).stream()
                .map(day -> new MonthDay(day.localDate(),
                        day.limitedMountainId() == null ? null : store.mountain(actor.id(), day.limitedMountainId()),
                        day.lockedMountainId() == null ? null : store.mountain(actor.id(), day.lockedMountainId()),
                        day.legacyReviewRequired(), byDate.getOrDefault(day.localDate(), List.of())))
                .toList();
        return new MonthSchedule(AvailabilityRange.ZONE.getId(), result);
    }

    private static String replacementFingerprint(List<PreparedDay> days) {
        StringBuilder canonical = new StringBuilder("REPLACE|");
        for (PreparedDay day : days) canonical.append(day.date()).append('|').append(day.start())
                .append('|').append(day.end()).append('|').append(day.mountainId()).append(';');
        return RequestKeys.fingerprint(canonical.toString());
    }

    @Override public Page<Slot> open(Actor actor, String from, String to, Integer limit, String cursor) {
        actor.require("STUDENT");
        LocalDate start, end;
        try { start = LocalDate.parse(from); end = LocalDate.parse(to); }
        catch (RuntimeException error) { throw new BusinessProblem(400, "请提供有效日期范围"); }
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 30)
            throw new BusinessProblem(400, "日期范围最多 31 天");
        Page<Slot> page = store.open(start, end, clock.instant(), RequestKeys.limit(limit), cursor);
        return new Page<>(page.items().stream().map(this::enrich).toList(), page.nextCursor());
    }

    @Override public Page<Slot> coachSlots(Actor actor, Integer limit, String cursor) {
        actor.require("COACH");
        Page<Slot> page = store.coachSlots(actor.id(), RequestKeys.limit(limit), cursor);
        return new Page<>(page.items().stream().map(this::enrich).toList(), page.nextCursor());
    }
    @Override public Slot find(String id) { return store.find(id); }
    @Override public Slot lock(String id) { return store.lock(id); }
    @Override public DayPolicy lockDay(String coachId, LocalDate date) { return store.lockDay(coachId, date); }
    @Override public Mountain lockMountain(String coachId, String mountainId) {
        return store.lockMountain(coachId, mountainId);
    }
    @Override public void lockDayToMountain(String coachId, LocalDate date, String mountainId) {
        store.lockDayToMountain(coachId, date, mountainId);
    }
    @Override public Mountain deactivateMountain(String coachId, String mountainId) {
        store.deactivateMountain(coachId, mountainId);
        return store.mountain(coachId, mountainId);
    }
    @Override public void markBooked(String id) {
        if (!store.markBooked(id)) throw new BusinessProblem(409, "时段已被确认");
    }
    @Override public void reopenBooked(String id) {
        if (!store.reopenBooked(id)) throw new BusinessProblem(409, "时段无法重新开放");
    }
    @Override public void unlockDayMountain(String coachId, LocalDate date) {
        store.unlockDayMountain(coachId, date);
    }

    private Slot enrich(Slot slot) {
        return new Slot(slot.id(), slot.coachId(), slot.courseId(), slot.location(), slot.zoneId(),
                slot.localDate(), slot.startAt(), slot.endAt(), slot.status(),
                store.availableMountains(slot.coachId(), slot.localDate()));
    }
    private Creation<Batch> batchReplay(SlotStore.BatchRecord record, String expected, List<Tail> tails) {
        if (!record.fingerprint().equals(expected)) throw new BusinessProblem(409, "同一请求键对应了不同排班内容");
        return new Creation<>(new Batch(store.batchSlots(record.id()).stream().map(this::enrich).toList(), tails), false);
    }
    private static Creation<Mountain> mountainReplay(Mountain mountain, String actual, String expected) {
        if (!expected.equals(actual)) throw new BusinessProblem(409, "同一请求键对应了不同雪场内容");
        return new Creation<>(mountain, false);
    }
    private List<PreparedDay> prepare(BatchRequest command) {
        if (command == null || command.days() == null || command.days().isEmpty() || command.days().size() > 31)
            throw new BusinessProblem(400, "每批请选择 1–31 个日期");
        List<PreparedDay> days = new ArrayList<>();
        HashSet<LocalDate> seen = new HashSet<>();
        int count = 0;
        for (DayInput input : command.days()) {
            if (input == null) throw new BusinessProblem(400, "请填写每天的可用时间");
            try {
                LocalDate date = LocalDate.parse(input.localDate());
                LocalTime start = LocalTime.parse(input.startTime());
                LocalTime end = LocalTime.parse(input.endTime());
                if (!seen.add(date)) throw new BusinessProblem(400, "同一批次不能重复选择日期");
                AvailabilityRange.Split split = AvailabilityRange.split(date, start, end);
                count += split.windows().size();
                if (count > 100) throw new BusinessProblem(400, "每批最多发布 100 个时段");
                days.add(new PreparedDay(date, start, end,
                        input.mountainId() == null || input.mountainId().isBlank() ? null : input.mountainId(), split));
            } catch (DateTimeException | IllegalArgumentException error) {
                throw new BusinessProblem(400, "日期或时间无效：" + error.getMessage());
            }
        }
        days.sort(Comparator.comparing(PreparedDay::date));
        return days;
    }
    private static List<Tail> tails(List<PreparedDay> days) {
        return days.stream().filter(day -> day.split().tailStart() != null)
                .map(day -> new Tail(day.date().toString(), day.split().tailStart().toString(),
                        day.split().tailEnd().toString())).toList();
    }
    private static String clean(String value, int max, String name) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw new BusinessProblem(400, name + "不能为空或过长");
        return value.strip();
    }
}
