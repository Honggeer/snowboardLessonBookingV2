package com.geer.snowboard.v2.bookings.application.port.out;

public interface BookingMailSender {
    void send(String email, String subject, String body);
}
