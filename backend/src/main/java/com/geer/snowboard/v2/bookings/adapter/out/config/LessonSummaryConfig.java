package com.geer.snowboard.v2.bookings.adapter.out.config;

import com.geer.snowboard.v2.bookings.application.port.out.LessonSummarySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class LessonSummaryConfig {
    @Bean LessonSummarySettings lessonSummarySettings(Environment environment) {
        return new LessonSummarySettings(environment.getProperty("booking.summary.reminders.enabled", Boolean.class, true));
    }
}
