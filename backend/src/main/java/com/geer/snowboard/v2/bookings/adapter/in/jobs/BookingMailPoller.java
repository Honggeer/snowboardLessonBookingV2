package com.geer.snowboard.v2.bookings.adapter.in.jobs;

import com.geer.snowboard.v2.bookings.application.port.in.BookingMailOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "booking.mail.worker.enabled", havingValue = "true", matchIfMissing = true)
public class BookingMailPoller {
    private final BookingMailOperations worker;

    public BookingMailPoller(BookingMailOperations worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${booking.mail.worker.delay-ms:10000}")
    public void poll() {
        worker.runOnce();
    }

    @Scheduled(cron = "0 20 * * * *")
    public void cleanup() {
        worker.cleanup();
    }
}
