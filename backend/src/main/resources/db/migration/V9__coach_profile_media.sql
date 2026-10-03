CREATE TABLE coach_profile_page (
    id TINYINT NOT NULL PRIMARY KEY,
    owner_id CHAR(36) NOT NULL,
    draft_version BIGINT NOT NULL DEFAULT 0,
    published_version BIGINT NOT NULL DEFAULT 0,
    schema_version INT NOT NULL DEFAULT 1,
    draft_json JSON NOT NULL,
    published_json JSON NOT NULL,
    CONSTRAINT ck_profile_singleton CHECK (id=1)
);
CREATE TABLE coach_profile_publish_request (
    owner_id CHAR(36) NOT NULL,
    request_key CHAR(36) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    published_version BIGINT NOT NULL,
    PRIMARY KEY (owner_id,request_key)
);
CREATE TABLE media_quota (
    owner_id CHAR(36) NOT NULL PRIMARY KEY,
    reserved_bytes BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_media_reserved CHECK (reserved_bytes>=0)
);
CREATE TABLE media_asset (
    id CHAR(36) NOT NULL PRIMARY KEY,
    owner_id CHAR(36) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    expected_size BIGINT NOT NULL,
    actual_size BIGINT NULL,
    staging_key VARCHAR(160) NOT NULL,
    source_version VARCHAR(256) NULL,
    frozen_key VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL,
    request_key CHAR(36) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    upload_expires_at DATETIME(6) NOT NULL,
    unreferenced_at DATETIME(6) NOT NULL,
    terminal_at DATETIME(6) NULL,
    width INT NOT NULL DEFAULT 0,
    height INT NOT NULL DEFAULT 0,
    duration_seconds DOUBLE NOT NULL DEFAULT 0,
    error_code VARCHAR(48) NULL,
    stage_cleaned_at DATETIME(6) NULL,
    quota_released BOOLEAN NOT NULL DEFAULT FALSE,
    sweep_cursor VARCHAR(512) NULL,
    sweep_version_cursor VARCHAR(512) NULL,
    sweep_at DATETIME(6) NULL,
    UNIQUE KEY uq_media_request (owner_id,request_key),
    UNIQUE KEY uq_media_staging (staging_key),
    UNIQUE KEY uq_media_frozen (frozen_key),
    INDEX ix_media_owner_status (owner_id,status),
    INDEX ix_media_clean (status,unreferenced_at),
    CONSTRAINT ck_media_status CHECK (status IN ('UPLOADING','VERIFYING','READY','REJECTED','FAILED','EXPIRED','DELETING','DELETED')),
    CONSTRAINT ck_media_size CHECK (expected_size>0)
);
CREATE TABLE media_reference (
    consumer VARCHAR(40) NOT NULL,
    slot VARCHAR(16) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    asset_id CHAR(36) NOT NULL,
    PRIMARY KEY (consumer,slot,purpose),
    INDEX ix_media_reference_asset (asset_id),
    FOREIGN KEY (asset_id) REFERENCES media_asset(id)
);
CREATE TABLE media_job (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    asset_id CHAR(36) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_run_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6) NULL,
    claim_token BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(48) NULL,
    UNIQUE KEY uq_media_job (asset_id,kind),
    INDEX ix_media_job_claim (status,next_run_at,lease_until),
    FOREIGN KEY (asset_id) REFERENCES media_asset(id)
);
