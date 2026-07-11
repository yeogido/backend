ALTER TABLE business_promotion
    ADD COLUMN short_description VARCHAR(255) NULL AFTER user_id,
    DROP COLUMN open_time,
    DROP COLUMN close_time;

ALTER TABLE business_operating_day
    ADD COLUMN open_time TIME NOT NULL AFTER day_of_week,
    ADD COLUMN close_time TIME NOT NULL AFTER open_time;

ALTER TABLE business_promotion_image
    ADD CONSTRAINT uk_business_promotion_image_promotion_sort_order
        UNIQUE (promotion_id, sort_order);