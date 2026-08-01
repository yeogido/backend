CREATE INDEX idx_course_list_latest
    ON course (course_type, deleted_at, created_at DESC, id DESC);

CREATE INDEX idx_course_list_recommend
    ON course (course_type, deleted_at, recommend_order, id DESC);

CREATE INDEX idx_course_list_region_latest
    ON course (course_type, deleted_at, region_id, created_at DESC, id DESC);
