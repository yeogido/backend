ALTER TABLE travel_record
    ADD COLUMN share_token VARCHAR(64) NULL,
    ADD CONSTRAINT uk_travel_record_share_token UNIQUE (share_token);
