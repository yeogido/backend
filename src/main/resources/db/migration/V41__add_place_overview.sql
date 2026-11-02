ALTER TABLE place
    ADD COLUMN overview TEXT NULL;

ALTER TABLE place
    ADD COLUMN overview_source VARCHAR(20) NULL;

ALTER TABLE place
    ADD COLUMN overview_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE place
    ADD COLUMN overview_attempted_at DATETIME(6) NULL;
