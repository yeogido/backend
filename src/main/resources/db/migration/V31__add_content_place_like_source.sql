ALTER TABLE place_like
    DROP CHECK chk_place_like_source_type,
    ADD CONSTRAINT chk_place_like_source_type
        CHECK (source_type IS NULL OR source_type IN ('COURSE_ITEM', 'CONTENT', 'PROMOTION'));
