ALTER TABLE content
    ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';

UPDATE content
SET publication_status = 'PENDING'
WHERE source = 'TOUR_API';
