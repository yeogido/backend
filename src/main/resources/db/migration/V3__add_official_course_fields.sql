ALTER TABLE course
    ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0 AFTER thumbnail_key,
    ADD COLUMN recommend_order INT NULL AFTER view_count;

CREATE INDEX idx_course_recommend_order
    ON course (recommend_order);