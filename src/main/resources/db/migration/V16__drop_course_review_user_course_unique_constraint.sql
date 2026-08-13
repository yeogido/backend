ALTER TABLE course_review
    ADD INDEX idx_course_review_user_id (user_id);

ALTER TABLE course_review
DROP INDEX uk_course_review_user_course;