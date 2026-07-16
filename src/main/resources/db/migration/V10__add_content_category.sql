ALTER TABLE content
ADD COLUMN category VARCHAR(20);

ALTER TABLE content
ADD CONSTRAINT chk_content_category
CHECK (
    category IN (
        'FESTIVAL',
        'PERFORMANCE',
        'EXHIBITION',
        'EXPERIENCE'
    )
);