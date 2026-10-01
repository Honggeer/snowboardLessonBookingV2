package com.geer.snowboard.v2.scheduling.application.port.in;

import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.Creation;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface SlotOperations {
    record Mountain(String id, String coachId, String name, boolean active) {}
    record DayPolicy(String coachId, LocalDate localDate, String limitedMountainId,
                     String lockedMountainId, boolean legacyReviewRequired) {}
    record Slot(String id, String coachId, String courseId, String location, String zoneId,
                LocalDate localDate, Instant startAt, Instant endAt, String status,
                List<Mountain> availableMountains) {}
    record DayInput(String localDate, String startTime, String endTime, String mountainId) {}
    record BatchRequest(List<DayInput> days) {}
    record Tail(String localDate, String startTime, String endTime) {}
    record Batch(List<Slot> slots, List<Tail> tails) {}
    record MonthDay(LocalDate localDate, Mountain limitedMountain, Mountain lockedMountain,
                    boolean legacyReviewRequired, List<Slot> slots) {}
    record MonthSchedule(String zoneId, List<MonthDay> days) {}
    record ReplacementLock(List<LocalDate> dates, Creation<Batch> replay) {}
    record MountainName(String name) {}

    Creation<Mountain> createMountain(Actor actor, MountainName command, String idempotencyKey);
    Page<Mountain> mountains(Actor actor, Integer limit, String cursor);
    Mountain renameMountain(Actor actor, String id, MountainName command);
    Creation<Batch> createBatch(Actor actor, BatchRequest command, String idempotencyKey);
    ReplacementLock lockReplacement(Actor actor, BatchRequest command, String idempotencyKey);
    Creation<Batch> finishReplacement(Actor actor, BatchRequest command, String idempotencyKey);
    MonthSchedule month(Actor actor, Integer year, Integer month);
    Page<Slot> open(Actor actor, String from, String to, Integer limit, String cursor);
    Page<Slot> coachSlots(Actor actor, Integer limit, String cursor);
    Slot find(String id);
    Slot lock(String id);
    DayPolicy lockDay(String coachId, LocalDate date);
    Mountain lockMountain(String coachId, String mountainId);
    void lockDayToMountain(String coachId, LocalDate date, String mountainId);
    Mountain deactivateMountain(String coachId, String mountainId);
    void markBooked(String id);
    void reopenBooked(String id);
    void unlockDayMountain(String coachId, LocalDate date);
}
