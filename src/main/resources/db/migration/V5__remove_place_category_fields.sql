SET @promotion_category_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'business_promotion'
      AND column_name = 'promotion_category'
);

SET @sql = IF(
    @promotion_category_exists = 0,
    'ALTER TABLE business_promotion ADD COLUMN promotion_category VARCHAR(20) NULL',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- 기존 홍보 데이터의 카테고리 값 채우기
UPDATE business_promotion bp
    JOIN place p ON p.id = bp.place_id
    SET bp.promotion_category =
        CASE
        WHEN p.category_group_code = 'CE7' THEN 'CAFE'
        WHEN p.category_group_code = 'FD6' THEN 'FOOD'
        WHEN p.category_group_code = 'CT1' THEN 'EXHIBITION'
        ELSE 'EXPERIENCE'
END
WHERE bp.promotion_category IS NULL;


-- 기존 데이터를 처리한 후 NOT NULL 적용
ALTER TABLE business_promotion
    MODIFY COLUMN promotion_category VARCHAR(20) NOT NULL;


-- category 컬럼이 있을 때만 삭제
SET @category_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'place'
      AND column_name = 'category'
);

SET @sql = IF(
    @category_exists > 0,
    'ALTER TABLE place DROP COLUMN category',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- category_group_name 컬럼이 있을 때만 삭제
SET @category_group_name_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'place'
      AND column_name = 'category_group_name'
);

SET @sql = IF(
    @category_group_name_exists > 0,
    'ALTER TABLE place DROP COLUMN category_group_name',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;