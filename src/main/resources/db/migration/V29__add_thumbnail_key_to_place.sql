ALTER TABLE place
    ADD COLUMN thumbnail_key VARCHAR(255) NULL;

UPDATE place p
JOIN (
    SELECT ci.place_id, MIN(ci.id) AS course_item_id
    FROM course_item ci
    JOIN course c ON c.id = ci.course_id
    WHERE ci.item_type = 'PLACE'
      AND ci.image_key IS NOT NULL
      AND c.deleted_at IS NULL
    GROUP BY ci.place_id
) selected ON selected.place_id = p.id
JOIN course_item ci ON ci.id = selected.course_item_id
SET p.thumbnail_key = ci.image_key
WHERE p.thumbnail_key IS NULL;
