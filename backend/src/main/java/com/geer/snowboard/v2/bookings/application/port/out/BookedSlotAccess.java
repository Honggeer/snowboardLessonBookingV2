package com.geer.snowboard.v2.bookings.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.Creation;

public interface BookedSlotAccess {
    record SlotSnapshot(String id, String coachId, String zoneId,
                        LocalDate localDate, Instant startAt, Instant endAt, String status) {}
    record DaySnapshot(String coachId, LocalDate localDate, String limitedMountainId,
                       String lockedMountainId, boolean legacyReviewRequired) {}
    record MountainSnapshot(String id, String coachId, String name, boolean active) {}
    record AvailabilityDay(String localDate, String startTime, String endTime, String mountainId) {}
    record AvailabilityRequest(List<AvailabilityDay> days) {}
    record AvailabilityTail(String localDate, String startTime, String endTime) {}
    record AvailabilityBatch(List<SlotSnapshot> slots, List<AvailabilityTail> tails) {}
    record ReplacementGate(List<LocalDate> dates, Creation<AvailabilityBatch> replay) {}
    SlotSnapshot find(String id);
    SlotSnapshot lock(String id);
    DaySnapshot lockDay(String coachId, LocalDate date);
    MountainSnapshot lockMountain(String coachId, String mountainId);
    void lockDayToMountain(String coachId, LocalDate date, String mountainId);
    MountainSnapshot deactivateMountain(String coachId, String mountainId);
    void markBooked(String id);
    void reopenBooked(String id);
    void unlockDayMountain(String coachId, LocalDate date);
    ReplacementGate lockReplacement(Actor actor, AvailabilityRequest command, String idempotencyKey);
    Creation<AvailabilityBatch> finishReplacement(Actor actor, AvailabilityRequest command, String idempotencyKey);
}
