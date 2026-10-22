ALTER TABLE course
    ADD COLUMN total_days INT NULL;

-- 기존 THREE_PLUS 코스의 실제 일수는 알 수 없으므로 최소 4일로 초기화한다.
-- 여행 기간이 없는 기존 코스는 1일로 초기화한다.
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

-- 기존 항목의 방문 순서와 이동 시간은 유지하고 모두 1일차에 배치한다.
ALTER TABLE course_item
    ADD COLUMN day_no INT NOT NULL DEFAULT 1;

-- course_id 외래 키를 지원하는 인덱스를 먼저 추가한 뒤 기존 인덱스를 제거한다.
ALTER TABLE course_item
    ADD CONSTRAINT uk_course_item_day_order UNIQUE (course_id, day_no, order_no);

ALTER TABLE course_item
    DROP INDEX uk_course_item_order,
    ADD CONSTRAINT chk_course_item_day CHECK (day_no >= 1);
