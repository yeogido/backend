ALTER TABLE place
    MODIFY COLUMN external_place_id VARCHAR(100) NOT NULL,
    ADD CONSTRAINT chk_place_external_place_id_not_blank
        CHECK (CHAR_LENGTH(TRIM(external_place_id)) > 0);