CREATE TABLE place_overview_api_operation (
    id VARCHAR(100) NOT NULL PRIMARY KEY,
    blocked_until DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

-- 다른 공급자·오, API 작업과 독립적인 공통 차단 상태의 초기 행 등록
INSERT INTO place_overview_api_operation (id, created_at, updated_at)
VALUES ('tour:/searchKeyword2', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
       ('tour:/detailCommon2', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
       ('tour:/ldongCode2', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
       ('daum:searchBlog', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
       ('daum:searchWeb', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
       ('gemini:generateContent', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
