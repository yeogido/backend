ALTER TABLE business_info
    ADD COLUMN place_id BIGINT NULL;

ALTER TABLE business_info
    ADD CONSTRAINT uk_business_info_place_id
        UNIQUE (place_id);

ALTER TABLE business_info
    ADD CONSTRAINT fk_business_info_place
        FOREIGN KEY (place_id) REFERENCES place(id);