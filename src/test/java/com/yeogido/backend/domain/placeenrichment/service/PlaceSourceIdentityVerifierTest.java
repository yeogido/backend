package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import com.yeogido.backend.domain.placeenrichment.dto.response.DaumSearchResponse;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceEvidenceSourceType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class PlaceSourceIdentityVerifierTest {
    private final TourPlaceRequest.Input input = new TourPlaceRequest.Input(
            "place-1", "테스트 카페", null, null, "서울 중구 테스트로 1", null, null, null,
            "02-123-4567", "https://official.example/places/1");

    @ParameterizedTest
    @CsvSource({
            "테스트 카페,서울 중구 테스트로 1,true",
            "테스트 카페,서울 중구 테스트로 1 다른 카페 서울 마포구 성미산로 165-7,false",
            "테스트 카페,서울 중구 테스트로 1 서울 중구 테스트로 1,true",
            "다른 카페,서울 중구 테스트로 1,false",
            "테스트 카페,커피 판매,false",
            "테스트 카페,02 123 4567 커피 판매,true",
            "테스트 카페,02-123-45678 커피 판매,false",
            "테스트 카페,02-999-4567 커피 판매,false",
            "테스트 카페,https://official.example/places/1 커피 판매,true",
            "테스트 카페,https://official.example/places/10 커피 판매,false",
            "테스트 카페,https://official.example/places/1?branch=other,false",
            "테스트 카페,https://official.example.evil/places/1,false",
            "테스트 카페,서울 중구 다른로 99 02-123-4567,false",
            "테스트 카페,서울 중구 테스트로1길 45 02-123-4567,false"
    })
    void checksEachSourceUsingTrustedInput(String title, String snippet, boolean expected) {
        assertThat(PlaceSourceIdentityVerifier.matches(input, new DaumSearchResponse.SearchSource(
                "S1", "https://review.example/post", PlaceEvidenceSourceType.REVIEW, title, snippet)))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "https://official.example/places/1,true",
            "https://official.example/places/1#intro,true",
            "https://official.example/places/2,false",
            "https://official.example/other,false",
            "https://official.example@evil.example/places/1,false"
    })
    void comparesFullOfficialUrlRatherThanHost(String url, boolean expected) {
        assertThat(PlaceSourceIdentityVerifier.matches(input, new DaumSearchResponse.SearchSource(
                "S1", url, PlaceEvidenceSourceType.OTHER, "테스트 카페", "커피 판매")))
                .isEqualTo(expected);
    }
}
