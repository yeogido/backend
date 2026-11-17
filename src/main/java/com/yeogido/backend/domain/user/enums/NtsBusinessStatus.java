package com.yeogido.backend.domain.user.enums;

import com.yeogido.backend.domain.user.dto.nts.NtsBusinessVerifyDTO;
import com.yeogido.backend.domain.user.exception.BusinessVerificationErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 국세청 사업자 상태 코드({@code b_stt_cd})를 애플리케이션 상태로 바꾸는 규칙.
 * <p>
 * 사업자 인증의 두 경로(진위확인 성공 후 영업 상태 확인, 진위확인 실패 후 상태 조회)가
 * 모두 이 규칙을 쓴다. 코드별 안내 메시지가 다르므로 상태와 에러코드를 한 곳에서 대응시킨다.
 */
@Getter
@RequiredArgsConstructor
public enum NtsBusinessStatus {

    /** 계속사업자 */
    ACTIVE("01"),

    /** 휴업자 */
    SUSPENDED("02"),

    /** 폐업자 */
    CLOSED("03"),

    /** 국세청에 등록되지 않은 번호 (빈 값 또는 알 수 없는 코드) */
    NOT_REGISTERED("");

    private final String code;

    /**
     * 상태 코드를 변환한다. {@code null}, 빈 값, 알 수 없는 코드는 모두 {@link #NOT_REGISTERED}로 본다.
     */
    public static NtsBusinessStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status != NOT_REGISTERED)
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElse(NOT_REGISTERED);
    }

    /**
     * 국세청 응답의 상태 객체를 변환한다. 상태 객체가 {@code null}이면 {@link #NOT_REGISTERED}로 본다.
     */
    public static NtsBusinessStatus from(NtsBusinessVerifyDTO.Status status) {
        if (status == null) {
            return NOT_REGISTERED;
        }

        return fromCode(status.businessStatusCode());
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 인증할 수 없는 상태에 대응하는 에러코드.
     *
     * @throws IllegalStateException 계속사업자는 거절 사유가 없으므로 호출하면 안 된다.
     */
    public BusinessVerificationErrorCode toErrorCode() {
        return switch (this) {
            case SUSPENDED -> BusinessVerificationErrorCode.BUSINESS_SUSPENDED;
            case CLOSED -> BusinessVerificationErrorCode.BUSINESS_CLOSED;
            case NOT_REGISTERED -> BusinessVerificationErrorCode.BUSINESS_NOT_REGISTERED;
            case ACTIVE -> throw new IllegalStateException(
                    "계속사업자는 인증 거절 사유가 없습니다"
            );
        };
    }
}
