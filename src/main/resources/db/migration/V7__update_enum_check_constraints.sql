-- region.type CHECK 수정
SET @region_type_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'region'
      AND constraint_name = 'chk_region_type'
      AND constraint_type = 'CHECK'
);

SET @sql = IF(
    @region_type_check_exists > 0,
    'ALTER TABLE region DROP CHECK chk_region_type',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE region
    ADD CONSTRAINT chk_region_type
        CHECK (type IN ('REGION', 'SUB_REGION'));


-- course.companion_type CHECK 수정
SET @course_companion_type_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'course'
      AND constraint_name = 'chk_course_companion_type'
      AND constraint_type = 'CHECK'
);

SET @sql = IF(
    @course_companion_type_check_exists > 0,
    'ALTER TABLE course DROP CHECK chk_course_companion_type',
    'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE course
    ADD CONSTRAINT chk_course_companion_type
        CHECK (companion_type IN ('SOLO', 'FRIEND', 'COUPLE', 'FAMILY', 'PET'));