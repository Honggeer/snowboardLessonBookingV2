CREATE TABLE scheduling_mountain (
    id CHAR(36) NOT NULL PRIMARY KEY,
    coach_id CHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    active_name VARCHAR(200) GENERATED ALWAYS AS (IF(active,name,NULL)) STORED,
    idempotency_key CHAR(36) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_mountain_coach FOREIGN KEY (coach_id) REFERENCES identity_account(id),
    CONSTRAINT uq_mountain_active_name UNIQUE (coach_id,active_name),
    CONSTRAINT uq_mountain_create UNIQUE (coach_id,idempotency_key),
    INDEX ix_mountain_active (coach_id,active,name,id)
);

CREATE TABLE scheduling_day (
    coach_id CHAR(36) NOT NULL,
    local_date DATE NOT NULL,
    limited_mountain_id CHAR(36) NULL,
    locked_mountain_id CHAR(36) NULL,
    legacy_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (coach_id,local_date),
    CONSTRAINT fk_day_coach FOREIGN KEY (coach_id) REFERENCES identity_account(id),
    CONSTRAINT fk_day_limited_mountain FOREIGN KEY (limited_mountain_id) REFERENCES scheduling_mountain(id),
    CONSTRAINT fk_day_locked_mountain FOREIGN KEY (locked_mountain_id) REFERENCES scheduling_mountain(id)
);

CREATE TABLE scheduling_batch (
    id CHAR(36) NOT NULL PRIMARY KEY,
    coach_id CHAR(36) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_batch_coach FOREIGN KEY (coach_id) REFERENCES identity_account(id),
    CONSTRAINT uq_batch_key UNIQUE (coach_id,idempotency_key)
);

ALTER TABLE scheduling_slot DROP FOREIGN KEY fk_slot_course;
ALTER TABLE scheduling_slot MODIFY course_id CHAR(36) NULL;
ALTER TABLE scheduling_slot MODIFY location VARCHAR(200) NULL;
ALTER TABLE scheduling_slot ADD batch_id CHAR(36) NULL;
ALTER TABLE scheduling_slot ADD CONSTRAINT fk_slot_batch FOREIGN KEY (batch_id) REFERENCES scheduling_batch(id);
ALTER TABLE scheduling_slot DROP INDEX ix_slot_public;
CREATE INDEX ix_slot_public_r2 ON scheduling_slot (status,local_date,start_at_utc,id);
CREATE INDEX ix_slot_batch ON scheduling_slot (batch_id,start_at_utc,id);

ALTER TABLE bookings_request ADD mountain_id CHAR(36) NULL;
ALTER TABLE bookings_request ADD CONSTRAINT fk_booking_mountain FOREIGN KEY (mountain_id) REFERENCES scheduling_mountain(id);
CREATE INDEX ix_booking_day_mountain ON bookings_request (coach_id,local_date_snapshot,mountain_id,status);
CREATE INDEX ix_booking_mountain_pending ON bookings_request (mountain_id,status);

-- Legacy free-text location may name a meeting place rather than a mountain.
-- Keep all historical rows and quarantine affected days until a reviewed mapping is supplied.
INSERT INTO scheduling_day (coach_id,local_date,legacy_review_required)
SELECT coach_id,local_date,TRUE FROM scheduling_slot GROUP BY coach_id,local_date;
