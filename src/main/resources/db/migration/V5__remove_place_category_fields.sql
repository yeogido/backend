ALTER TABLE place
DROP COLUMN category,
    DROP COLUMN category_group_name;

ALTER TABLE business_promotion
    ADD COLUMN promotion_category VARCHAR(20) NOT NULL;