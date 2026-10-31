-- 기존 여행 기록의 내용은 유지하고 공개범위와 수정 버전만 초기화
ALTER TABLE travel_record
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_travel_record_visibility
        CHECK (visibility IN ('PRIVATE', 'FRIENDS', 'PUBLIC'));

-- 생성자는 travel_record.user_id에 유지하고 수락한 추가 참여자만 저장
CREATE TABLE travel_record_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    travel_record_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_travel_record_member_record_user UNIQUE (travel_record_id, user_id),
    INDEX idx_travel_record_member_user_record (user_id, travel_record_id),
    CONSTRAINT fk_travel_record_member_record
        FOREIGN KEY (travel_record_id) REFERENCES travel_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_record_member_user
        FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE travel_record_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    travel_record_id BIGINT NOT NULL,
    inviter_user_id BIGINT NOT NULL,
    invitee_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_travel_record_invitation_record_invitee
        UNIQUE (travel_record_id, invitee_user_id),
    INDEX idx_travel_record_invitation_invitee_status_created (invitee_user_id, status, created_at),
    INDEX idx_travel_record_invitation_inviter (inviter_user_id),
    CONSTRAINT fk_travel_record_invitation_record
        FOREIGN KEY (travel_record_id) REFERENCES travel_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_record_invitation_inviter
        FOREIGN KEY (inviter_user_id) REFERENCES `user` (id) ON DELETE RESTRICT,
    CONSTRAINT fk_travel_record_invitation_invitee
        FOREIGN KEY (invitee_user_id) REFERENCES `user` (id) ON DELETE RESTRICT,
    CONSTRAINT chk_travel_record_invitation_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
