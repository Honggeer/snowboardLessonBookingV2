package com.geer.snowboard.v2.bookings.adapter.in.web;

import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations;
import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Apply;
import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Booking;
import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Reject;
import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Cancel;
import com.geer.snowboard.v2.bootstrap.ActorResolver;
import com.geer.snowboard.v2.bootstrap.BookingLockRetry;
import com.geer.snowboard.v2.sharedkernel.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class BookingController {
    private final BookingOperations bookings;
    private final ActorResolver actors;
    private final BookingLockRetry retry;
    public BookingController(BookingOperations bookings, ActorResolver actors, BookingLockRetry retry) {
        this.bookings = bookings; this.actors = actors; this.retry = retry;
    }

    @PostMapping("/api/bookings")
    public ResponseEntity<Booking> apply(@RequestHeader("Idempotency-Key") String key, @RequestBody Apply body) {
        var actor = actors.current();
        var result = retry.run(() -> bookings.apply(actor, body, key));
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.value());
    }
    @GetMapping("/api/bookings/mine")
    public Page<Booking> mine(@RequestParam(required = false) Integer limit,
                              @RequestParam(required = false) String cursor) {
        return bookings.mine(actors.current(), limit, cursor);
    }
    @GetMapping("/api/coach/bookings")
    public Page<Booking> coach(@RequestParam(required = false) String status,
                               @RequestParam(required = false) Integer limit,
                               @RequestParam(required = false) String cursor) {
        return bookings.coach(actors.current(), status, limit, cursor);
    }
    @PostMapping("/api/coach/bookings/{id}/confirm")
    public Booking confirm(@PathVariable String id) {
        var actor = actors.current();
        return retry.run(() -> bookings.confirm(actor, id));
    }
    @PostMapping("/api/coach/bookings/{id}/reject")
    public Booking reject(@PathVariable String id, @RequestBody(required = false) Reject body) {
        var actor = actors.current();
        return retry.run(() -> bookings.reject(actor, id, body));
    }
    @PostMapping("/api/bookings/{id}/cancel")
    public Booking cancel(@PathVariable String id, @RequestBody(required = false) Cancel body) {
        var actor = actors.current();
        return retry.run(() -> bookings.cancel(actor, id, body));
    }
    @PostMapping("/api/coach/mountains/{id}/deactivate")
    public BookingOperations.MountainResult deactivateMountain(@PathVariable String id) {
        var actor = actors.current();
        return retry.run(() -> bookings.deactivateMountain(actor, id));
    }
    @PostMapping("/api/coach/availability/replacements")
    public ResponseEntity<BookingOperations.AvailabilityBatch> replaceAvailability(
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody BookingOperations.ReplacementRequest body) {
        var actor = actors.current();
        var result = retry.run(() -> bookings.replaceAvailability(actor, body, key));
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.value());
    }
}
