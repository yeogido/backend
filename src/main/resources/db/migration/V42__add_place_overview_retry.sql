ALTER TABLE place
    ADD COLUMN overview_attempt_count INT NOT NULL DEFAULT 0;

-- 기존 처리 이력의 최초 시도 1회 반영
UPDATE place
SET overview_attempt_count = 1
WHERE overview_status <> 'PENDING';

CREATE INDEX idx_place_overview_retry
    ON place (overview_status, overview_attempted_at, id);
