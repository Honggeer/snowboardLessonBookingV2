package com.geer.snowboard.v2.bookings.application.port.in;

public interface BookingMailOperations {
    void runOnce();
    void cleanup();
}
