package com.geer.snowboard.v2.bookings;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.geer.snowboard.v2.bookings.adapter.in.jobs.BookingMailPoller;
import com.geer.snowboard.v2.bookings.application.port.in.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class BookingMailPollerTest {
    @Test void pollingSurvivesFailureUsesOneDedicatedThreadAndStopsOnClose() throws Exception {
        var worker=mock(BookingMailOperations.class); var reminders=mock(BookingReminderOperations.class);
        var calls=new AtomicInteger();var thread=new AtomicReference<Thread>();var recovered=new CountDownLatch(1);
        doAnswer(invocation->{thread.set(Thread.currentThread());if(calls.incrementAndGet()==1) throw new IllegalStateException("fake failure");recovered.countDown();return null;}).when(worker).runOnce();
        var poller=new BookingMailPoller(worker,reminders,20);
        try { poller.start();assertThat(recovered.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(thread.get().getName()).isEqualTo("booking-mail-poller");verify(reminders,timeout(1000).times(1)).backfill();
        } finally { poller.close(); }
        thread.get().join(1000);
        assertThat(thread.get().isAlive()).isFalse();
    }
    @Test void invalidDelayIsRejectedBeforeStartingBackgroundWork() {
        assertThatThrownBy(()->new BookingMailPoller(mock(BookingMailOperations.class),mock(BookingReminderOperations.class),0)).isInstanceOf(IllegalArgumentException.class);
    }
}
