ALTER TABLE `user`
    DROP CHECK chk_user_gender;

ALTER TABLE `user`
    ADD CONSTRAINT chk_user_gender
        CHECK (gender IN ('MALE', 'FEMALE', 'NONE'));
