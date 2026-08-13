CREATE TABLE place_operating_day (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_place_operating_day_place_day UNIQUE (place_id, day_of_week),
    INDEX idx_place_operating_day_place_id (place_id),
    CONSTRAINT fk_place_operating_day_place
        FOREIGN KEY (place_id) REFERENCES place (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_place_operating_day_day CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_item_time (
    id BIGINT NOT NULL AUTO_INCREMENT,
    from_course_item_id BIGINT NOT NULL,
    to_course_item_id BIGINT NOT NULL,
    transport_mode VARCHAR(20) NOT NULL,
    duration_minutes INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_item_time_items_mode
        UNIQUE (from_course_item_id, to_course_item_id, transport_mode),
    INDEX idx_course_item_time_from_course_item_id (from_course_item_id),
    INDEX idx_course_item_time_to_course_item_id (to_course_item_id),
    CONSTRAINT fk_course_item_time_from_course_item
        FOREIGN KEY (from_course_item_id) REFERENCES course_item (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_course_item_time_to_course_item
        FOREIGN KEY (to_course_item_id) REFERENCES course_item (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_course_item_time_transport_mode CHECK (
        transport_mode IN ('WALK', 'PUBLIC', 'CAR')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
