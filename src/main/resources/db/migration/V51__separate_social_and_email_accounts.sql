-- 소셜 계정은 provider + provider_id로 식별하며 이메일을 공유할 수 있다.
ALTER TABLE `user`
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'EMAIL';

UPDATE `user` u
SET u.account_type = 'SOCIAL'
WHERE EXISTS (SELECT 1 FROM social_account s WHERE s.user_id = u.id);

-- MySQL UNIQUE는 여러 NULL을 허용한다. 이메일 가입만 DB에서도 중복을 제한한다.
-- 탈퇴 계정도 키를 유지하여 기존 이메일 재가입 제한 정책을 보존한다.
ALTER TABLE `user`
    ADD COLUMN email_account_key VARCHAR(255)
        GENERATED ALWAYS AS (CASE WHEN account_type = 'EMAIL' THEN email ELSE NULL END) STORED,
    ADD CONSTRAINT chk_user_account_type CHECK (account_type IN ('EMAIL', 'SOCIAL')),
    ADD CONSTRAINT uk_user_email_account UNIQUE (email_account_key),
    ADD INDEX idx_user_email_account_type (email, account_type),
    DROP INDEX uk_user_email;
