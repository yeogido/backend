-- 사용자 상태 변경의 행 잠금과 분리하여 초대 응답/회원 탈퇴를 직렬화합니다.
-- 사용자 FK를 두지 않아 잠금 행 초기화가 사용자 행 잠금 순서에 영향을 주지 않습니다.
-- 탈퇴 후 늦은 요청도 동일한 잠금을 사용하므로 잠금 행은 유지합니다.
CREATE TABLE travel_record_participation_lock (
    user_id BIGINT NOT NULL,
    PRIMARY KEY (user_id)
);
