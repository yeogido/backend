package com.yeogido.backend.domain.content.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TourContentSyncServiceTest {

    @Test
    void firstPhoneNumber_extractsFirstNumberFromLabeledText() {
        assertThat(TourContentSyncServiceImpl.firstPhoneNumber(
                "행사장 02-319-1220 운영사 031-123-4567"
        )).isEqualTo("02-319-1220");
    }

    @Test
    void firstPhoneNumber_normalizesSpaceSeparatedNumber() {
        assertThat(TourContentSyncServiceImpl.firstPhoneNumber(
                "서울 02-786-0610 수원 031 123 4567"
        )).isEqualTo("02-786-0610");
    }

    @Test
    void firstPhoneNumber_keepsShortRepresentativeNumber() {
        assertThat(TourContentSyncServiceImpl.firstPhoneNumber("문의 02-120"))
                .isEqualTo("02-120");
    }
}
