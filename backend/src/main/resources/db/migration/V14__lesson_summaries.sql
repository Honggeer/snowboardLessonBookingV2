CREATE TABLE bookings_lesson_summary (
    booking_id CHAR(36) NOT NULL PRIMARY KEY,
    state VARCHAR(16) NOT NULL,
    practice_content VARCHAR(3000) NOT NULL,
    improvement_areas VARCHAR(3000) NOT NULL,
    reinforcement_exercises VARCHAR(3000) NOT NULL,
    version BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    CONSTRAINT fk_lesson_summary_booking FOREIGN KEY (booking_id) REFERENCES bookings_request(id),
    CONSTRAINT ck_lesson_summary_version CHECK (version > 0),
    CONSTRAINT ck_lesson_summary_state CHECK (
        (state='DRAFT' AND published_at IS NULL)
        OR (state='PUBLISHED' AND published_at IS NOT NULL
            AND CHAR_LENGTH(TRIM(practice_content))>0
            AND CHAR_LENGTH(TRIM(improvement_areas))>0
            AND CHAR_LENGTH(TRIM(reinforcement_exercises))>0))
) DEFAULT CHARSET=utf8mb4;

ALTER TABLE bookings_request
    ADD COLUMN lesson_summary_reminder_planned_at DATETIME(6) NULL,
    ADD INDEX ix_booking_summary_plan (status,lesson_summary_reminder_planned_at,end_at_utc_snapshot,id),
    ADD INDEX ix_booking_student_completed (student_id,status,end_at_utc_snapshot,id),
    ADD INDEX ix_booking_coach_completed (coach_id,status,end_at_utc_snapshot,id);

-- A durable checked marker excludes lessons that ended before the first rollout.
-- It records planning, not delivery; it must survive task cleanup and restarts.
SET @lesson_summary_rollout_at = UTC_TIMESTAMP(6);
UPDATE bookings_request SET lesson_summary_reminder_planned_at=@lesson_summary_rollout_at
WHERE end_at_utc_snapshot<=@lesson_summary_rollout_at;

ALTER TABLE bookings_mail_task
    ADD COLUMN summary_end_at_utc DATETIME(6) NULL,
    DROP CHECK ck_booking_mail_event,
    DROP CHECK ck_booking_reminder_start,
    ADD CONSTRAINT ck_booking_mail_event CHECK (event_type IN
        ('APPLICATION_RECEIVED','BOOKING_CONFIRMED','BOOKING_REJECTED',
         'STUDENT_LESSON_REMINDER','COACH_LESSON_REMINDER','COACH_SUMMARY_REMINDER','STUDENT_SUMMARY_PUBLISHED')),
    ADD CONSTRAINT ck_booking_reminder_start CHECK (
        (event_type IN ('STUDENT_LESSON_REMINDER','COACH_LESSON_REMINDER') AND reminder_start_at_utc IS NOT NULL)
        OR (event_type NOT IN ('STUDENT_LESSON_REMINDER','COACH_LESSON_REMINDER') AND reminder_start_at_utc IS NULL)),
    ADD CONSTRAINT ck_booking_summary_end CHECK (
        (event_type='COACH_SUMMARY_REMINDER' AND summary_end_at_utc IS NOT NULL)
        OR (event_type<>'COACH_SUMMARY_REMINDER' AND summary_end_at_utc IS NULL));
