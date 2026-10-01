package com.geer.snowboard.v2.scheduling.adapter.in.web;

import com.geer.snowboard.v2.bootstrap.ActorResolver;
import com.geer.snowboard.v2.bootstrap.BookingLockRetry;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.Batch;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.BatchRequest;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.Mountain;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.MountainName;
import com.geer.snowboard.v2.scheduling.application.port.in.SlotOperations.Slot;
import com.geer.snowboard.v2.sharedkernel.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SlotController {
    private final SlotOperations slots;
    private final ActorResolver actors;
    private final BookingLockRetry retry;
    public SlotController(SlotOperations slots, ActorResolver actors, BookingLockRetry retry) {
        this.slots = slots; this.actors = actors; this.retry = retry;
    }

    @GetMapping("/api/slots")
    public Page<Slot> open(@RequestParam String from, @RequestParam String to,
                           @RequestParam(required = false) Integer limit,
                           @RequestParam(required = false) String cursor) {
        return slots.open(actors.current(), from, to, limit, cursor);
    }
    @GetMapping("/api/coach/slots")
    public Page<Slot> coach(@RequestParam(required = false) Integer limit,
                            @RequestParam(required = false) String cursor) {
        return slots.coachSlots(actors.current(), limit, cursor);
    }
    @GetMapping("/api/coach/availability/month")
    public SlotOperations.MonthSchedule month(@RequestParam(required = false) Integer year,
                                               @RequestParam(required = false) Integer month) {
        return slots.month(actors.current(), year, month);
    }
    @GetMapping("/api/coach/mountains")
    public Page<Mountain> mountains(@RequestParam(required = false) Integer limit,
                                    @RequestParam(required = false) String cursor) {
        return slots.mountains(actors.current(), limit, cursor);
    }
    @PostMapping("/api/coach/mountains")
    public ResponseEntity<Mountain> createMountain(@RequestHeader("Idempotency-Key") String key,
                                                    @RequestBody MountainName body) {
        var actor = actors.current();
        var result = retry.run(() -> slots.createMountain(actor, body, key));
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.value());
    }
    @PatchMapping("/api/coach/mountains/{id}")
    public Mountain renameMountain(@PathVariable String id, @RequestBody MountainName body) {
        var actor = actors.current();
        return retry.run(() -> slots.renameMountain(actor, id, body));
    }
    @PostMapping("/api/coach/availability/batches")
    public ResponseEntity<Batch> createBatch(@RequestHeader("Idempotency-Key") String key,
                                             @RequestBody BatchRequest body) {
        var actor = actors.current();
        var result = retry.run(() -> slots.createBatch(actor, body, key));
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.value());
    }
}
