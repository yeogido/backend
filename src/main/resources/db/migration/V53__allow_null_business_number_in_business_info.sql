-- 해제(REVOKED)된 사업자 정보는 사업자번호와 장소 연결을 비워
-- 같은 사업자번호와 장소를 다른 인증에서 다시 쓸 수 있게 한다.
-- uk_business_info_business_number, uk_business_info_place_id 는 NULL 을 여러 건 허용하므로 유지한다.
-- chk_business_info_business_number 는 NULL 이면 UNKNOWN 으로 평가되어 통과하므로 유지한다.
ALTER TABLE business_info
    MODIFY COLUMN business_number VARCHAR(10) NULL;
