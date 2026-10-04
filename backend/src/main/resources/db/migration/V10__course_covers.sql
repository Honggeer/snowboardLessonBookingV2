ALTER TABLE catalog_course
    ADD COLUMN cover_asset_id CHAR(36) NULL,
    ADD COLUMN cover_position_x DECIMAL(5,2) NOT NULL DEFAULT 50,
    ADD COLUMN cover_position_y DECIMAL(5,2) NOT NULL DEFAULT 50,
    ADD CONSTRAINT ck_course_cover_x CHECK (cover_position_x BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_course_cover_y CHECK (cover_position_y BETWEEN 0 AND 100);

ALTER TABLE media_reference MODIFY COLUMN consumer VARCHAR(64) NOT NULL;
