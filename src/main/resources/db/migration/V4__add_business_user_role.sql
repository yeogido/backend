ALTER TABLE `user`
DROP CHECK chk_user_role;

ALTER TABLE `user`
    ADD CONSTRAINT chk_user_role
        CHECK (role IN ('USER', 'BUSINESS', 'ADMIN'));