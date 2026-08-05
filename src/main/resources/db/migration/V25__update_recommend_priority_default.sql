UPDATE content
SET recommend_priority = 9999
WHERE recommend_priority IS NULL;

ALTER TABLE content
MODIFY COLUMN recommend_priority INT NOT NULL DEFAULT 9999;