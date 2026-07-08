CREATE TABLE region (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parent_id BIGINT NULL,
    name VARCHAR(50) NOT NULL,
    full_name VARCHAR(50) NULL,
    type VARCHAR(20) NOT NULL,
    image_key VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_region_parent_id (parent_id),
    CONSTRAINT fk_region_parent
        FOREIGN KEY (parent_id) REFERENCES region (id),
    CONSTRAINT chk_region_type CHECK (type IN ('SIDO', 'SIGUNGU'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nickname VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NULL,
    gender VARCHAR(10) NOT NULL,
    birth_year VARCHAR(4) NOT NULL,
    region_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    profile_image VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_email UNIQUE (email),
    INDEX idx_user_region_id (region_id),
    CONSTRAINT fk_user_region
        FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT chk_user_gender CHECK (gender IN ('MALE', 'FEMALE')),
    CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE social_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_provider_provider_id UNIQUE (provider, provider_id),
    INDEX idx_social_account_user_id (user_id),
    CONSTRAINT fk_social_account_user
        FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT chk_social_account_provider CHECK (provider IN ('KAKAO', 'NAVER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE place (
    id BIGINT NOT NULL AUTO_INCREMENT,
    region_id BIGINT NOT NULL,
    external_place_id VARCHAR(100) NULL,
    source VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NULL,
    category_group_code VARCHAR(20) NULL,
    category_group_name VARCHAR(50) NULL,
    road_address VARCHAR(255) NULL,
    lot_address VARCHAR(255) NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_place_source_external_id UNIQUE (source, external_place_id),
    INDEX idx_place_region_id (region_id),
    CONSTRAINT fk_place_region
        FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT chk_place_source CHECK (source IN ('KAKAO', 'TOUR_API')),
    CONSTRAINT chk_place_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_place_longitude CHECK (longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hashtag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hashtag_name VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE content (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    external_content_id VARCHAR(100) NULL,
    source VARCHAR(20) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    thumbnail_image VARCHAR(500) NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    contact_phone VARCHAR(20) NULL,
    official_url VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_content_place_id (place_id),
    CONSTRAINT fk_content_place
        FOREIGN KEY (place_id) REFERENCES place (id),
    CONSTRAINT chk_content_source CHECK (source IN ('TOUR_API')),
    CONSTRAINT chk_content_date_range CHECK (
        start_date IS NULL OR end_date IS NULL OR start_date <= end_date
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE place_like (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_place_like_user_place UNIQUE (user_id, place_id),
    INDEX idx_place_like_place_id (place_id),
    CONSTRAINT fk_place_like_place
        FOREIGN KEY (place_id) REFERENCES place (id),
    CONSTRAINT fk_place_like_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE content_like (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_content_like_user_content UNIQUE (user_id, content_id),
    INDEX idx_content_like_content_id (content_id),
    CONSTRAINT fk_content_like_content
        FOREIGN KEY (content_id) REFERENCES content (id),
    CONSTRAINT fk_content_like_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE content_hashtag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content_id BIGINT NOT NULL,
    hashtag_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_content_hashtag UNIQUE (content_id, hashtag_id),
    INDEX idx_content_hashtag_hashtag_id (hashtag_id),
    CONSTRAINT fk_content_hashtag_content
        FOREIGN KEY (content_id) REFERENCES content (id),
    CONSTRAINT fk_content_hashtag_hashtag
        FOREIGN KEY (hashtag_id) REFERENCES hashtag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    region_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NULL,
    course_type VARCHAR(20) NOT NULL,
    duration_type VARCHAR(20) NULL,
    transport_type VARCHAR(20) NULL,
    month_start INT NULL,
    month_end INT NULL,
    companion_type VARCHAR(20) NULL,
    thumbnail_key VARCHAR(255) NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_course_region_id (region_id),
    INDEX idx_course_deleted_at (deleted_at),
    CONSTRAINT fk_course_region
        FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT chk_course_type CHECK (course_type IN ('OFFICIAL', 'USER')),
    CONSTRAINT chk_course_duration_type CHECK (
        duration_type IS NULL OR duration_type IN ('DAY_TRIP', 'ONE_NIGHT', 'TWO_NIGHT', 'THREE_PLUS')
    ),
    CONSTRAINT chk_course_transport_type CHECK (
        transport_type IS NULL OR transport_type IN ('WALK', 'CAR', 'PUBLIC')
    ),
    CONSTRAINT chk_course_companion_type CHECK (
        companion_type IS NULL OR companion_type IN ('SOLO', 'FRIEND', 'COUPLE', 'FAMILY', 'CHILD')
    ),
    CONSTRAINT chk_course_month_start CHECK (month_start IS NULL OR month_start BETWEEN 1 AND 12),
    CONSTRAINT chk_course_month_end CHECK (month_end IS NULL OR month_end BETWEEN 1 AND 12),
    CONSTRAINT chk_course_month_range CHECK (
        month_start IS NULL OR month_end IS NULL OR month_start <= month_end
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    place_id BIGINT NULL,
    content_id BIGINT NULL,
    item_type VARCHAR(20) NOT NULL,
    order_no INT NOT NULL,
    image_key VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_item_order UNIQUE (course_id, order_no),
    INDEX idx_course_item_place_id (place_id),
    INDEX idx_course_item_content_id (content_id),
    CONSTRAINT fk_course_item_course
        FOREIGN KEY (course_id) REFERENCES course (id),
    CONSTRAINT fk_course_item_place
        FOREIGN KEY (place_id) REFERENCES place (id),
    CONSTRAINT fk_course_item_content
        FOREIGN KEY (content_id) REFERENCES content (id),
    CONSTRAINT chk_course_item_type CHECK (item_type IN ('PLACE', 'CONTENT')),
    CONSTRAINT chk_course_item_target CHECK (
        (item_type = 'PLACE' AND place_id IS NOT NULL AND content_id IS NULL)
        OR
        (item_type = 'CONTENT' AND place_id IS NULL AND content_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    rating DECIMAL(2, 1) NOT NULL,
    content TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_review_user_course UNIQUE (user_id, course_id),
    INDEX idx_course_review_course_id (course_id),
    CONSTRAINT fk_course_review_user
        FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_course_review_course
        FOREIGN KEY (course_id) REFERENCES course (id),
    CONSTRAINT chk_course_review_rating CHECK (rating >= 0 AND rating <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_like (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_like_user_course UNIQUE (user_id, course_id),
    INDEX idx_course_like_course_id (course_id),
    CONSTRAINT fk_course_like_course
        FOREIGN KEY (course_id) REFERENCES course (id),
    CONSTRAINT fk_course_like_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_hashtag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    hashtag_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_hashtag UNIQUE (course_id, hashtag_id),
    INDEX idx_course_hashtag_hashtag_id (hashtag_id),
    CONSTRAINT fk_course_hashtag_course
        FOREIGN KEY (course_id) REFERENCES course (id),
    CONSTRAINT fk_course_hashtag_hashtag
        FOREIGN KEY (hashtag_id) REFERENCES hashtag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE travel_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    cover_image_key VARCHAR(255) NOT NULL,
    folder_theme VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_travel_record_user_id (user_id),
    INDEX idx_travel_record_region_id (region_id),
    CONSTRAINT fk_travel_record_user
        FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_travel_record_region
        FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT chk_travel_record_folder_theme CHECK (folder_theme IS NULL OR folder_theme IN ('BASIC')),
    CONSTRAINT chk_travel_record_date_range CHECK (start_date <= end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE travel_record_photo (
    id BIGINT NOT NULL AUTO_INCREMENT,
    travel_record_id BIGINT NOT NULL,
    image_key VARCHAR(255) NOT NULL,
    image_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_record_photo_order UNIQUE (travel_record_id, image_order),
    CONSTRAINT fk_travel_record_photo_record
        FOREIGN KEY (travel_record_id) REFERENCES travel_record (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE travel_record_sticker (
    id BIGINT NOT NULL AUTO_INCREMENT,
    travel_record_id BIGINT NOT NULL,
    sticker_key VARCHAR(255) NOT NULL,
    position_x DOUBLE NOT NULL,
    position_y DOUBLE NOT NULL,
    rotation DOUBLE NULL,
    scale DOUBLE NULL,
    z_index INT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_travel_record_sticker_record_id (travel_record_id),
    CONSTRAINT fk_travel_record_sticker_record
        FOREIGN KEY (travel_record_id) REFERENCES travel_record (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE business_promotion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    place_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    owner_comment TEXT NULL,
    open_time TIME(6) NOT NULL,
    close_time TIME(6) NOT NULL,
    phone_number VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL,
    sns_account VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_business_promotion_place UNIQUE (place_id),
    INDEX idx_business_promotion_user_id (user_id),
    CONSTRAINT fk_business_promotion_place
        FOREIGN KEY (place_id) REFERENCES place (id),
    CONSTRAINT fk_business_promotion_user
        FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT chk_business_promotion_status CHECK (status IN ('ACTIVE', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE business_operating_day (
    id BIGINT NOT NULL AUTO_INCREMENT,
    promotion_id BIGINT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_business_operating_day_promotion_day UNIQUE (promotion_id, day_of_week),
    CONSTRAINT fk_business_operating_day_promotion
        FOREIGN KEY (promotion_id) REFERENCES business_promotion (id),
    CONSTRAINT chk_business_operating_day_day CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE business_promotion_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    promotion_id BIGINT NOT NULL,
    image_key VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_business_promotion_image_promotion_id (promotion_id),
    CONSTRAINT fk_business_promotion_image_promotion
        FOREIGN KEY (promotion_id) REFERENCES business_promotion (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE business_promotion_hashtag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    promotion_id BIGINT NOT NULL,
    hashtag_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_business_promotion_hashtag UNIQUE (promotion_id, hashtag_id),
    INDEX idx_business_promotion_hashtag_hashtag_id (hashtag_id),
    CONSTRAINT fk_business_promotion_hashtag_promotion
        FOREIGN KEY (promotion_id) REFERENCES business_promotion (id),
    CONSTRAINT fk_business_promotion_hashtag_hashtag
        FOREIGN KEY (hashtag_id) REFERENCES hashtag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
