ALTER TABLE bookings_mail_task
    DROP CHECK ck_booking_mail_event,
    DROP CHECK ck_booking_reminder_start,
    ADD CONSTRAINT ck_booking_mail_event CHECK (event_type IN
        ('APPLICATION_RECEIVED','BOOKING_CONFIRMED','BOOKING_REJECTED',
         'STUDENT_LESSON_REMINDER','COACH_LESSON_REMINDER')),
    ADD CONSTRAINT ck_booking_reminder_start CHECK (
        (event_type IN ('APPLICATION_RECEIVED','BOOKING_CONFIRMED','BOOKING_REJECTED')
            AND reminder_start_at_utc IS NULL)
        OR (event_type IN ('STUDENT_LESSON_REMINDER','COACH_LESSON_REMINDER')
            AND reminder_start_at_utc IS NOT NULL));
