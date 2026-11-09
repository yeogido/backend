-- 외부 API 복구 확인에 따른 빈 설명의 최종 실패 장소 일회성 초기화
-- 기존 설명 및 다른 처리 상태 보존
UPDATE place
SET overview_status = 'PENDING',
    overview_attempt_count = 0,
    overview_attempted_at = NULL,
    overview_next_retry_at = NULL,
    overview_source = NULL,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE overview_status = 'FAILED'
  AND (overview IS NULL OR TRIM(overview) = '');
