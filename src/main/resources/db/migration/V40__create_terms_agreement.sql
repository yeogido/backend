CREATE TABLE terms_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL,
    version VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    effective_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_terms_type_version UNIQUE (type, version),
    CONSTRAINT uk_terms_type_effective UNIQUE (type, effective_at),
    CONSTRAINT chk_terms_type CHECK (type IN ('SERVICE', 'PRIVACY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_terms_agreement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    terms_document_id BIGINT NOT NULL,
    agreed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_terms_document UNIQUE (user_id, terms_document_id),
    CONSTRAINT fk_agreement_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_agreement_document FOREIGN KEY (terms_document_id) REFERENCES terms_document (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Do not manufacture consent for existing members or seed unapproved legal text.
