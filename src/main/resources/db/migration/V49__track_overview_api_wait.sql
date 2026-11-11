ALTER TABLE place
    ADD COLUMN overview_blocked_by_operation VARCHAR(100) NULL;

CREATE INDEX idx_place_overview_api_wait
    ON place (overview_blocked_by_operation, overview_status, overview_next_retry_at);

ALTER TABLE place_overview_api_operation
    ADD COLUMN manual_release_required BOOLEAN NOT NULL DEFAULT FALSE;
