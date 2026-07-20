ALTER TABLE content
DROP CHECK chk_content_source;

ALTER TABLE content
ADD CONSTRAINT chk_content_source
CHECK (
    source IN ('TOUR_API','KAKAO_API','ADMIN')
);