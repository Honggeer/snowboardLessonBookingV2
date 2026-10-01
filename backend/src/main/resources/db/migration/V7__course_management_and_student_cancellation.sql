ALTER TABLE catalog_course
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE bookings_request
    DROP CHECK ck_booking_status,
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

ALTER TABLE bookings_request
    ADD CONSTRAINT ck_booking_status
        CHECK (status IN ('PENDING','CONFIRMED','REJECTED','CANCELLED_BY_STUDENT'));

ALTER TABLE bookings_request
    DROP INDEX uq_booking_student_slot;

ALTER TABLE bookings_request
    ADD COLUMN active_slot_guard CHAR(36)
        GENERATED ALWAYS AS (IF(status='CANCELLED_BY_STUDENT',NULL,slot_id)) STORED,
    ADD CONSTRAINT uq_booking_active_student_slot UNIQUE (student_id,active_slot_guard);
