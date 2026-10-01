package com.geer.snowboard.v2.bookings.domain;

public enum BookingStatus {
    PENDING, CONFIRMED, REJECTED, CANCELLED_BY_STUDENT;

    public boolean mayConfirm() { return this == PENDING; }
    public boolean mayReject() { return this == PENDING; }
}
