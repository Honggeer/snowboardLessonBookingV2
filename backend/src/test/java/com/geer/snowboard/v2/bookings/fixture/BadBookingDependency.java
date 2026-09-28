package com.geer.snowboard.v2.bookings.fixture;

import com.geer.snowboard.v2.scheduling.domain.TimeWindow;

/** Deliberately invalid cross-module reference used only by ArchitectureTest. */
public class BadBookingDependency {

    public TimeWindow forbiddenReference() {
        return null;
    }
}
