ALTER TABLE place_like
    ADD COLUMN source_type VARCHAR(20) NULL,
    ADD COLUMN source_id BIGINT NULL,
    ADD CONSTRAINT chk_place_like_source_type
        CHECK (source_type IS NULL OR source_type IN ('COURSE_ITEM', 'PROMOTION')),
    ADD CONSTRAINT chk_place_like_source_pair
        CHECK (
            (source_type IS NULL AND source_id IS NULL)
            OR (source_type IS NOT NULL AND source_id IS NOT NULL)
        );
