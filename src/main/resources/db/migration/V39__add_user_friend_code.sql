ALTER TABLE `user`
    ADD COLUMN friend_code VARCHAR(8) NULL;

-- 친구코드 문자 집합 (헷갈리는 0/O, 1/I 제외 32자)
SET @friend_code_chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

-- 기존 사용자에게 8자리 친구코드 일괄 생성
UPDATE `user`
SET friend_code = CONCAT(
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1)
);

-- 중복된 코드만 다시 생성 (2회 반복 후에도 중복이 남으면 아래 UNIQUE 제약 추가에서 실패)
UPDATE `user` u
    JOIN (SELECT friend_code FROM `user` GROUP BY friend_code HAVING COUNT(*) > 1) duplicated
    ON u.friend_code = duplicated.friend_code
SET u.friend_code = CONCAT(
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1)
);

UPDATE `user` u
    JOIN (SELECT friend_code FROM `user` GROUP BY friend_code HAVING COUNT(*) > 1) duplicated
    ON u.friend_code = duplicated.friend_code
SET u.friend_code = CONCAT(
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1),
    SUBSTRING(@friend_code_chars, FLOOR(RAND() * 32) + 1, 1)
);

ALTER TABLE `user`
    MODIFY COLUMN friend_code VARCHAR(8) NOT NULL,
    ADD CONSTRAINT uk_user_friend_code UNIQUE (friend_code);
