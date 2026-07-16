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