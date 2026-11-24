-- 기존 사진의 실제 업로더는 복원할 수 없어 NULL로 유지합니다.
ALTER TABLE travel_record_photo
    ADD COLUMN uploader_id BIGINT NULL,
    ADD INDEX idx_travel_record_photo_uploader (uploader_id),
    ADD CONSTRAINT fk_travel_record_photo_uploader FOREIGN KEY (uploader_id) REFERENCES `user` (id);

-- 탈퇴자의 사진만 있던 폴더도 사진 없는 상태로 유지합니다.
ALTER TABLE travel_record
    MODIFY COLUMN cover_image_key VARCHAR(255) NULL;
