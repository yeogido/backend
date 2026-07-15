CREATE TABLE course_review_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_review_id BIGINT NOT NULL,
    image_key VARCHAR(255) NOT NULL,
    image_order INT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_review_image_review_order UNIQUE (course_review_id, image_order),
    CONSTRAINT fk_course_review_image_review
        FOREIGN KEY (course_review_id) REFERENCES course_review (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
