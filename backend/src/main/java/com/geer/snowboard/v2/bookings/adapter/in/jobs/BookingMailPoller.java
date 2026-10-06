package com.geer.snowboard.v2.bookings.adapter.in.jobs;

import com.geer.snowboard.v2.bookings.application.port.in.BookingMailOperations;
import com.geer.snowboard.v2.bookings.application.port.in.BookingReminderOperations;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="booking.mail.worker.enabled",havingValue="true",matchIfMissing=true)
public final class BookingMailPoller {
    private static final System.Logger LOG=System.getLogger(BookingMailPoller.class.getName());
    private final BookingMailOperations worker;
    private final BookingReminderOperations reminders;
    private final long delayMillis;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(action -> {
        Thread thread=new Thread(action,"booking-mail-poller"); thread.setDaemon(true); return thread;
    });
    public BookingMailPoller(BookingMailOperations worker,BookingReminderOperations reminders,
                             @Value("${booking.mail.worker.delay-ms:10000}") long delayMillis) {
        if (delayMillis<1) throw new IllegalArgumentException("Booking mail delay must be positive");
        this.worker=worker; this.reminders=reminders; this.delayMillis=delayMillis;
    }
    @PostConstruct public void start() {
        scheduler.scheduleWithFixedDelay(()->safe(reminders::backfill),1,60000,TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(()->safe(worker::runOnce),1,delayMillis,TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(()->safe(worker::cleanup),3600000,3600000,TimeUnit.MILLISECONDS);
    }
    private void safe(Runnable action) {
        try { action.run(); }
        catch (Exception exception) { LOG.log(System.Logger.Level.WARNING,"Booking mail polling failed; persisted work will be retried ({0})",exception.getClass().getSimpleName()); }
    }
    @PreDestroy public void close() {
        scheduler.shutdownNow();
        try { scheduler.awaitTermination(5,TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }
}
