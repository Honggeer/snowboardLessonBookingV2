ALTER TABLE identity_account
    ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE identity_password_reset (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id CHAR(36) NOT NULL,
    code_digest BINARY(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    wrong_attempts INT NOT NULL DEFAULT 0,
    consumed_at DATETIME(6) NULL,
    invalidated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_password_reset_account FOREIGN KEY (account_id) REFERENCES identity_account(id),
    INDEX ix_password_reset_account (account_id, created_at)
);

CREATE TABLE identity_password_reset_mail_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reset_id CHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    claim_until DATETIME(6) NULL,
    last_error VARCHAR(255) NULL,
    CONSTRAINT fk_password_reset_mail FOREIGN KEY (reset_id) REFERENCES identity_password_reset(id),
    INDEX ix_password_reset_mail_claim (status, next_attempt_at, claim_until)
);
