ALTER TABLE course
    ADD COLUMN user_id BIGINT NULL AFTER region_id;

CREATE INDEX idx_course_user_id
    ON course (user_id);

ALTER TABLE course
    ADD CONSTRAINT fk_course_user
        FOREIGN KEY (user_id) REFERENCES `user` (id);

ALTER TABLE course
    DROP CHECK chk_course_type;

ALTER TABLE course
    ADD CONSTRAINT chk_course_type CHECK (course_type IN ('OFFICIAL', 'LOCAL'));
