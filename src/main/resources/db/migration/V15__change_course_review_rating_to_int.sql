UPDATE course_review
SET rating = ROUND(rating);

UPDATE course_review
SET rating = 1
WHERE rating < 1;

UPDATE course_review
SET rating = 5
WHERE rating > 5;

ALTER TABLE course_review
    DROP CHECK chk_course_review_rating;

ALTER TABLE course_review
    MODIFY COLUMN rating INT NOT NULL;

ALTER TABLE course_review
    ADD CONSTRAINT chk_course_review_rating
        CHECK (rating BETWEEN 1 AND 5);
