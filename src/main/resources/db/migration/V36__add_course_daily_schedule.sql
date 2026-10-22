ALTER TABLE course
    ADD COLUMN total_days INT NULL;

-- 기존 THREE_PLUS 코스의 실제 일수 정보 부재로 최소 4일로 초기화
-- 여행 기간이 없는 기존 코스는 1일로 초기화
UPDATE course
SET total_days = CASE duration_type
    WHEN 'ONE_NIGHT' THEN 2
    WHEN 'TWO_NIGHT' THEN 3
    WHEN 'THREE_PLUS' THEN 4
    ELSE 1
END;

ALTER TABLE course
    MODIFY COLUMN total_days INT NOT NULL,
    ADD CONSTRAINT chk_course_total_days CHECK (total_days >= 1);

-- 기존 항목의 방문 순서와 이동 시간 유지 및 전체 항목의 1일차 배치
ALTER TABLE course_item
    ADD COLUMN day_no INT NOT NULL DEFAULT 1;

-- course_id 외래 키 지원을 위해 새 인덱스 추가 후 기존 인덱스 제거
ALTER TABLE course_item
    ADD CONSTRAINT uk_course_item_day_order UNIQUE (course_id, day_no, order_no);

ALTER TABLE course_item
    DROP INDEX uk_course_item_order,
    ADD CONSTRAINT chk_course_item_day CHECK (day_no >= 1);
