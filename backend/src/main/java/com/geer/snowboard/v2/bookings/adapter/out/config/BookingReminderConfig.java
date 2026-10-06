package com.geer.snowboard.v2.bookings.adapter.out.config;

import com.geer.snowboard.v2.bookings.application.port.out.BookingReminderSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class BookingReminderConfig {
    @Bean BookingReminderSettings bookingReminderSettings(Environment environment) {
        return new BookingReminderSettings(environment.getProperty("booking.reminders.enabled", Boolean.class, true));
    }
}
