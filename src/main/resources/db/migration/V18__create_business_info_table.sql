CREATE TABLE business_info (
       id BIGINT NOT NULL AUTO_INCREMENT,
       user_id BIGINT NOT NULL,
       business_number VARCHAR(10) NOT NULL,
       opening_date DATE NOT NULL,
       representative_name VARCHAR(100) NOT NULL,
       registration_image_key VARCHAR(255) NOT NULL,
       business_name VARCHAR(100) NOT NULL,
       business_address VARCHAR(255) NOT NULL,
       verification_status VARCHAR(20) NOT NULL,
       verified_at DATETIME(6) NOT NULL,
       created_at DATETIME(6) NOT NULL,
       updated_at DATETIME(6) NOT NULL,

       PRIMARY KEY (id),

       CONSTRAINT uk_business_info_business_number
           UNIQUE (business_number),

       INDEX idx_business_info_user_id (user_id),

       CONSTRAINT fk_business_info_user
           FOREIGN KEY (user_id) REFERENCES `user` (id),

       CONSTRAINT chk_business_info_business_number
           CHECK (business_number REGEXP '^[0-9]{10}$'),

       CONSTRAINT chk_business_info_verification_status
           CHECK (verification_status IN ('APPROVED', 'REVOKED'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;