-- 두 사용자 사이의 친구 요청·친구 관계 (한 쌍당 한 행, 거절·취소·삭제 시 행 삭제)
CREATE TABLE friendship (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requester_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    user_low_id BIGINT NOT NULL,
    user_high_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 요청 방향과 관계없이 같은 두 사람의 행이 하나만 존재하도록 작은 ID·큰 ID 조합으로 제약
    CONSTRAINT uk_friendship_pair UNIQUE (user_low_id, user_high_id),
    INDEX idx_friendship_requester_status (requester_id, status),
    INDEX idx_friendship_receiver_status (receiver_id, status),
    CONSTRAINT fk_friendship_requester
        FOREIGN KEY (requester_id) REFERENCES `user` (id),
    CONSTRAINT fk_friendship_receiver
        FOREIGN KEY (receiver_id) REFERENCES `user` (id),
    CONSTRAINT chk_friendship_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT chk_friendship_pair CHECK (
        user_low_id < user_high_id
        AND user_low_id = LEAST(requester_id, receiver_id)
        AND user_high_id = GREATEST(requester_id, receiver_id)
    )
);
