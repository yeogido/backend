package com.yeogido.backend.domain.user.enums;

import com.yeogido.backend.domain.user.dto.nts.NtsBusinessVerifyDTO;
import com.yeogido.backend.domain.user.exception.BusinessVerificationErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NtsBusinessStatusTest {

    @ParameterizedTest
    @CsvSource({
            "01, ACTIVE",
            "02, SUSPENDED",
            "03, CLOSED"
    })
    void fromCode_MapsKnownCodes(String code, NtsBusinessStatus expected) {
        assertThat(NtsBusinessStatus.fromCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"99", "abc"})
    void fromCode_MapsUnknownOrBlankCodesToNotRegistered(String code) {
        assertThat(NtsBusinessStatus.fromCode(code))
                .isEqualTo(NtsBusinessStatus.NOT_REGISTERED);
    }

    @Test
    void from_WhenStatusIsNull_ReturnsNotRegistered() {
        assertThat(NtsBusinessStatus.from(null))
                .isEqualTo(NtsBusinessStatus.NOT_REGISTERED);
    }

    @Test
    void from_UsesBusinessStatusCodeOfStatus() {
        NtsBusinessVerifyDTO.Status status = new NtsBusinessVerifyDTO.Status(
                "1234567890", "폐업자", "03", "부가가치세 일반과세자", "01", "20240101"
        );

        assertThat(NtsBusinessStatus.from(status)).isEqualTo(NtsBusinessStatus.CLOSED);
    }

    @ParameterizedTest
    @CsvSource({
            "SUSPENDED, BUSINESS_SUSPENDED",
            "CLOSED, BUSINESS_CLOSED",
            "NOT_REGISTERED, BUSINESS_NOT_REGISTERED"
    })
    void toErrorCode_MapsInactiveStatuses(
            NtsBusinessStatus status,
            BusinessVerificationErrorCode expected
    ) {
        assertThat(status.isActive()).isFalse();
        assertThat(status.toErrorCode()).isEqualTo(expected);
    }

    @Test
    void toErrorCode_WhenActive_Throws() {
        assertThat(NtsBusinessStatus.ACTIVE.isActive()).isTrue();
        assertThatThrownBy(NtsBusinessStatus.ACTIVE::toErrorCode)
                .isInstanceOf(IllegalStateException.class);
    }
}
