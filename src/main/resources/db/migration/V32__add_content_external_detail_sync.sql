ALTER TABLE content
    MODIFY COLUMN description TEXT NULL,
    ADD COLUMN external_details_synced_at DATETIME(6) NULL;

CREATE TABLE content_external_link
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id    BIGINT        NOT NULL,
    link_type     VARCHAR(30)   NOT NULL,
    source        VARCHAR(20)   NOT NULL,
    label         VARCHAR(100)  NULL,
    url           VARCHAR(1000) NOT NULL,
    url_hash      CHAR(64) GENERATED ALWAYS AS (SHA2(url, 256)) STORED,
    display_order INT           NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,

    CONSTRAINT fk_content_external_link_content
        FOREIGN KEY (content_id) REFERENCES content (id) ON DELETE CASCADE,
    CONSTRAINT uk_content_external_link_content_url
        UNIQUE (content_id, url_hash),
    CONSTRAINT chk_content_external_link_type
        CHECK (link_type IN ('OFFICIAL_WEBSITE', 'INSTAGRAM', 'FACEBOOK', 'YOUTUBE', 'BLOG', 'ETC')),
    CONSTRAINT chk_content_external_link_source
        CHECK (source IN ('TOUR_API', 'ADMIN'))
);

CREATE INDEX idx_content_external_link_content_order
    ON content_external_link (content_id, display_order);

INSERT IGNORE INTO content_external_link
    (content_id, link_type, source, label, url, display_order, created_at, updated_at)
SELECT c.id,
       CASE
           WHEN LOWER(c.official_url) LIKE '%instagram.com%' THEN 'INSTAGRAM'
           WHEN LOWER(c.official_url) LIKE '%facebook.com%' THEN 'FACEBOOK'
           WHEN LOWER(c.official_url) LIKE '%youtube.com%'
               OR LOWER(c.official_url) LIKE '%youtu.be%' THEN 'YOUTUBE'
           WHEN LOWER(c.official_url) LIKE '%blog.naver.com%'
               OR LOWER(c.official_url) LIKE '%blog.daum.net%' THEN 'BLOG'
           ELSE 'OFFICIAL_WEBSITE'
       END,
       CASE WHEN c.source = 'TOUR_API' THEN 'TOUR_API' ELSE 'ADMIN' END,
       CASE
           WHEN LOWER(c.official_url) LIKE '%instagram.com%' THEN '공식 인스타그램'
           WHEN LOWER(c.official_url) LIKE '%facebook.com%' THEN '공식 페이스북'
           WHEN LOWER(c.official_url) LIKE '%youtube.com%'
               OR LOWER(c.official_url) LIKE '%youtu.be%' THEN '공식 유튜브'
           WHEN LOWER(c.official_url) LIKE '%blog.naver.com%'
               OR LOWER(c.official_url) LIKE '%blog.daum.net%' THEN '공식 블로그'
           ELSE '공식 홈페이지'
       END,
       c.official_url,
       0,
       NOW(6),
       NOW(6)
FROM content c
WHERE c.official_url IS NOT NULL
  AND c.official_url <> '';

ALTER TABLE content
    DROP COLUMN official_url;

-- 기존 관광공사 콘텐츠도 상세정보와 링크를 한 번 다시 동기화
UPDATE content
SET external_details_synced_at = NULL
WHERE source = 'TOUR_API';
