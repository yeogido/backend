DROP TABLE IF EXISTS travel_record_sticker;

CREATE TABLE sticker (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    name VARCHAR(50) NOT NULL,
    image_key VARCHAR(255) NOT NULL,
    sticker_type VARCHAR(20) NOT NULL,
    category VARCHAR(20) NOT NULL,
    display_order INT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sticker_image_key UNIQUE (image_key),
    INDEX idx_sticker_type_category (sticker_type, category),
    INDEX idx_sticker_user_type_deleted (user_id, sticker_type, deleted_at),
    CONSTRAINT fk_sticker_user
        FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT chk_sticker_type CHECK (sticker_type IN ('DEFAULT', 'CUSTOM')),
    CONSTRAINT chk_sticker_category CHECK (category IN ('NATURE', 'FOOD', 'ANIMAL', 'PERSON', 'OBJECT', 'CUSTOM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE travel_record_sticker (
    id BIGINT NOT NULL AUTO_INCREMENT,
    travel_record_id BIGINT NOT NULL,
    sticker_id BIGINT NOT NULL,
    position_x DOUBLE NOT NULL,
    position_y DOUBLE NOT NULL,
    rotation DOUBLE NOT NULL,
    scale DOUBLE NOT NULL,
    z_index INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_travel_record_sticker_record_z_index UNIQUE (travel_record_id, z_index),
    INDEX idx_travel_record_sticker_record_id (travel_record_id),
    INDEX idx_travel_record_sticker_sticker_id (sticker_id),
    CONSTRAINT fk_travel_record_sticker_record
        FOREIGN KEY (travel_record_id) REFERENCES travel_record (id),
    CONSTRAINT fk_travel_record_sticker_sticker
        FOREIGN KEY (sticker_id) REFERENCES sticker (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
