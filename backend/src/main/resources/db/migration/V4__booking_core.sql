CREATE TABLE catalog_course (
    id CHAR(36) NOT NULL PRIMARY KEY,
    coach_id CHAR(36) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    price_amount DECIMAL(10,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CAD',
    idempotency_key CHAR(36) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_course_coach FOREIGN KEY (coach_id) REFERENCES identity_account(id),
    CONSTRAINT uq_course_create UNIQUE (coach_id,idempotency_key),
    CONSTRAINT ck_course_price CHECK (price_amount >= 0),
    CONSTRAINT ck_course_currency CHECK (currency = 'CAD'),
    INDEX ix_course_page (created_at,id)
);

CREATE TABLE scheduling_coach_guard (
    coach_id CHAR(36) NOT NULL PRIMARY KEY,
    CONSTRAINT fk_schedule_guard_coach FOREIGN KEY (coach_id) REFERENCES identity_account(id)
);

CREATE TABLE scheduling_slot (
    id CHAR(36) NOT NULL PRIMARY KEY,
    coach_id CHAR(36) NOT NULL,
    course_id CHAR(36) NOT NULL,
    location VARCHAR(200) NOT NULL,
    zone_id VARCHAR(100) NOT NULL,
    local_date DATE NOT NULL,
    start_at_utc DATETIME(6) NOT NULL,
    end_at_utc DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    idempotency_key CHAR(36) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_slot_coach FOREIGN KEY (coach_id) REFERENCES identity_account(id),
    CONSTRAINT fk_slot_course FOREIGN KEY (course_id) REFERENCES catalog_course(id),
    CONSTRAINT uq_slot_create UNIQUE (coach_id,idempotency_key),
    CONSTRAINT ck_slot_status CHECK (status IN ('OPEN','BOOKED')),
    CONSTRAINT ck_slot_interval CHECK (start_at_utc < end_at_utc),
    INDEX ix_slot_public (course_id,status,local_date,start_at_utc,id),
    INDEX ix_slot_coach_interval (coach_id,start_at_utc,end_at_utc)
);

CREATE TABLE bookings_student_guard (
    student_id CHAR(36) NOT NULL PRIMARY KEY,
    CONSTRAINT fk_booking_guard_student FOREIGN KEY (student_id) REFERENCES identity_account(id)
);

CREATE TABLE bookings_request (
    id CHAR(36) NOT NULL PRIMARY KEY,
    slot_id CHAR(36) NOT NULL,
    course_id CHAR(36) NOT NULL,
    coach_id CHAR(36) NOT NULL,
    student_id CHAR(36) NOT NULL,
    student_name_snapshot VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decision_reason VARCHAR(200) NULL,
    course_title_snapshot VARCHAR(100) NOT NULL,
    price_amount_snapshot DECIMAL(10,2) NOT NULL,
    currency_snapshot CHAR(3) NOT NULL,
    location_snapshot VARCHAR(200) NOT NULL,
    zone_id_snapshot VARCHAR(100) NOT NULL,
    local_date_snapshot DATE NOT NULL,
    start_at_utc_snapshot DATETIME(6) NOT NULL,
    end_at_utc_snapshot DATETIME(6) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    confirmed_slot_guard CHAR(36) GENERATED ALWAYS AS (IF(status='CONFIRMED',slot_id,NULL)) STORED,
    CONSTRAINT fk_booking_slot FOREIGN KEY (slot_id) REFERENCES scheduling_slot(id),
    CONSTRAINT fk_booking_course FOREIGN KEY (course_id) REFERENCES catalog_course(id),
    CONSTRAINT fk_booking_student FOREIGN KEY (student_id) REFERENCES identity_account(id),
    CONSTRAINT uq_booking_student_slot UNIQUE (student_id,slot_id),
    CONSTRAINT uq_booking_request_key UNIQUE (student_id,idempotency_key),
    CONSTRAINT uq_booking_confirmed_slot UNIQUE (confirmed_slot_guard),
    CONSTRAINT ck_booking_status CHECK (status IN ('PENDING','CONFIRMED','REJECTED')),
    INDEX ix_booking_student_page (student_id,created_at,id),
    INDEX ix_booking_coach_page (coach_id,created_at,id),
    INDEX ix_booking_slot_status (slot_id,status)
);
