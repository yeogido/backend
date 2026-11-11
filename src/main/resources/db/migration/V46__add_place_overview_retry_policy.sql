ALTER TABLE place
    ADD COLUMN overview_next_retry_at DATETIME(6) NULL;

CREATE INDEX idx_place_overview_next_retry
    ON place (overview_status, overview_next_retry_at, id);

-- 기존 FAILED 상태의 최종 실패 유지
-- 배포 및 공급자 한도와 장애 회복 확인 후 별도 절차를 통한 명시적 복구
