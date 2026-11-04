-- 대기 초대는 초대 당시의 친구 관계에 연결합니다.
ALTER TABLE travel_record_invitation
    ADD COLUMN friendship_id BIGINT NULL,
    ADD INDEX idx_travel_record_invitation_friendship (friendship_id);

-- 기존 대기 초대 중 당시부터 유지된 수락 완료 친구 관계만 연결합니다.
UPDATE travel_record_invitation invitation
JOIN friendship friend
    ON friend.user_low_id = LEAST(invitation.inviter_user_id, invitation.invitee_user_id)
    AND friend.user_high_id = GREATEST(invitation.inviter_user_id, invitation.invitee_user_id)
    AND friend.status = 'ACCEPTED'
    AND friend.created_at <= invitation.created_at
SET invitation.friendship_id = friend.id
WHERE invitation.status = 'PENDING';

-- 친구가 아니거나, 친구 해제 후 다시 맺은 관계의 옛 대기 초대는 복구하지 않습니다.
DELETE FROM travel_record_invitation WHERE status = 'PENDING' AND friendship_id IS NULL;

ALTER TABLE travel_record_invitation
    ADD CONSTRAINT fk_travel_record_invitation_friendship
        FOREIGN KEY (friendship_id) REFERENCES friendship (id) ON DELETE CASCADE;

-- 수락, 거절 시 friendship_id를 NULL로 해제하므로 이후 친구 삭제는 참여 관계에 영향을 주지 않습니다.
