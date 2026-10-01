package com.geer.snowboard.v2.scheduling.application.port.out;

import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.DayPolicy;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.Mountain;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.Slot;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface SlotStore {
    record BatchRecord(String id, String fingerprint) {}

    Mountain mountainByKey(String coachId, String key);
    String mountainFingerprint(String coachId, String key);
    boolean insertMountain(Mountain mountain, String key, String fingerprint);
    Mountain mountain(String coachId, String id);
    Mountain lockMountain(String coachId, String id);
    Page<Mountain> mountains(String coachId, int limit, String cursor);
    List<Mountain> activeMountains(String coachId);
    void renameMountain(String coachId, String id, String name);
    void deactivateMountain(String coachId, String id);

    void lockCoach(String coachId);
    void ensureDay(String coachId, LocalDate date, String limitedMountainId);
    DayPolicy lockDay(String coachId, LocalDate date);
    DayPolicy day(String coachId, LocalDate date);
    void lockDayToMountain(String coachId, LocalDate date, String mountainId);
    void replaceDayLimit(String coachId, LocalDate date, String mountainId);
    List<DayPolicy> daysInMonth(String coachId, LocalDate first, LocalDate last);
    List<Slot> slotsInMonth(String coachId, LocalDate first, LocalDate last);
    List<Slot> lockActiveDaySlots(String coachId, LocalDate date);
    void closeOpenDaySlots(String coachId, LocalDate date);
    boolean overlaps(String coachId, Instant start, Instant end);
    BatchRecord batchByKey(String coachId, String key);
    void insertBatch(String id, String coachId, String key, String fingerprint);
    void insertSlot(Slot slot, String batchId, String fingerprint);
    List<Slot> batchSlots(String batchId);
    Page<Slot> open(LocalDate from, LocalDate to, Instant now, int limit, String cursor);
    Page<Slot> coachSlots(String coachId, int limit, String cursor);
    List<Mountain> availableMountains(String coachId, LocalDate date);
    Slot find(String id);
    Slot lock(String id);
    boolean markBooked(String id);
    boolean reopenBooked(String id);
    void unlockDayMountain(String coachId, LocalDate date);
}
