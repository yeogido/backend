CREATE TABLE course_embedding (
    course_id BIGINT NOT NULL,
    model VARCHAR(128) NOT NULL,
    dimensions INT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    vector_json LONGTEXT NOT NULL,
    indexed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (course_id),
    CONSTRAINT fk_course_embedding_course FOREIGN KEY (course_id) REFERENCES course (id),
    CONSTRAINT chk_course_embedding_dimensions CHECK (dimensions > 0)
);
