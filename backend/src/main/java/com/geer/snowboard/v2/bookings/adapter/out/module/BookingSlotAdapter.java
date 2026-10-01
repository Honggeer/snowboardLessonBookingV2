package com.geer.snowboard.v2.bookings.adapter.out.module;

import com.geer.snowboard.v2.bookings.application.port.out.BookedSlotAccess;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations;
import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.Creation;
import org.springframework.stereotype.Component;

@Component
public final class BookingSlotAdapter implements BookedSlotAccess {
    private final SlotOperations slots;
    public BookingSlotAdapter(SlotOperations slots) { this.slots = slots; }
    @Override public SlotSnapshot find(String id) { return snapshot(slots.find(id)); }
    @Override public SlotSnapshot lock(String id) {
        return snapshot(slots.lock(id));
    }
    @Override public DaySnapshot lockDay(String coachId, java.time.LocalDate date) {
        var day = slots.lockDay(coachId, date);
        return day == null ? null : new DaySnapshot(day.coachId(), day.localDate(),
                day.limitedMountainId(), day.lockedMountainId(), day.legacyReviewRequired());
    }
    @Override public MountainSnapshot lockMountain(String coachId, String mountainId) {
        return mountain(slots.lockMountain(coachId, mountainId));
    }
    @Override public void lockDayToMountain(String coachId, java.time.LocalDate date, String mountainId) {
        slots.lockDayToMountain(coachId, date, mountainId);
    }
    @Override public MountainSnapshot deactivateMountain(String coachId, String mountainId) {
        return mountain(slots.deactivateMountain(coachId, mountainId));
    }
    @Override public void markBooked(String id) { slots.markBooked(id); }
    @Override public void reopenBooked(String id) { slots.reopenBooked(id); }
    @Override public void unlockDayMountain(String coachId, java.time.LocalDate date) {
        slots.unlockDayMountain(coachId, date);
    }
    @Override public ReplacementGate lockReplacement(Actor actor, AvailabilityRequest command, String key) {
        var result = slots.lockReplacement(actor, request(command), key);
        return new ReplacementGate(result.dates(), result.replay() == null ? null
                : new Creation<>(batch(result.replay().value()), false));
    }
    @Override public Creation<AvailabilityBatch> finishReplacement(Actor actor, AvailabilityRequest command, String key) {
        var result = slots.finishReplacement(actor, request(command), key);
        return new Creation<>(batch(result.value()), result.created());
    }
    private static SlotOperations.BatchRequest request(AvailabilityRequest command) {
        return new SlotOperations.BatchRequest(command == null || command.days() == null ? null
                : command.days().stream().map(day -> day == null ? null :
                new SlotOperations.DayInput(day.localDate(), day.startTime(), day.endTime(), day.mountainId())).toList());
    }
    private static AvailabilityBatch batch(SlotOperations.Batch batch) {
        return new AvailabilityBatch(batch.slots().stream().map(BookingSlotAdapter::snapshot).toList(),
                batch.tails().stream().map(tail -> new AvailabilityTail(tail.localDate(), tail.startTime(), tail.endTime())).toList());
    }
    private static SlotSnapshot snapshot(SlotOperations.Slot slot) {
        return slot == null ? null : new SlotSnapshot(slot.id(), slot.coachId(), slot.zoneId(),
                slot.localDate(), slot.startAt(), slot.endAt(), slot.status());
    }
    private static MountainSnapshot mountain(SlotOperations.Mountain mountain) {
        return mountain == null ? null : new MountainSnapshot(mountain.id(), mountain.coachId(),
                mountain.name(), mountain.active());
    }
}
