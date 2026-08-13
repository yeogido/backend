UPDATE content
SET recommend_priority = 0
WHERE recommend_priority IS NULL;

ALTER TABLE content
MODIFY COLUMN recommend_priority INT NOT NULL DEFAULT 0;
