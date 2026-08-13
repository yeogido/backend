package com.yeogido.backend.domain.content.converter;

import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentConverterTest {

    @Test
    void toPlaceInfo_IncludesFieldsRequiredToRestorePlaceSelection() {
        Place place = Place.builder()
                .id(1L)
                .externalPlaceId("external-place-1")
                .source(PlaceSource.KAKAO)
                .name("테스트 장소")
                .roadAddress("서울시 도로명 주소")
                .lotAddress("서울시 지번 주소")
                .latitude(new BigDecimal("37.1234567"))
                .longitude(new BigDecimal("127.1234567"))
                .build();

        ContentResDTO.PlaceInfo result = ContentConverter.toPlaceInfo(place);

        assertThat(result.externalPlaceId()).isEqualTo("external-place-1");
        assertThat(result.source()).isEqualTo(PlaceSource.KAKAO);
        assertThat(result.lotAddress()).isEqualTo("서울시 지번 주소");
    }

    @Test
    void toContentDetailRes_IncludesFieldsRequiredToRestoreEditForm() {
        Content content = Content.builder()
                .id(1L)
                .source(ContentSource.ADMIN)
                .title("테스트 문화콘텐츠")
                .description("설명")
                .category(ContentCategory.FESTIVAL)
                .build();

        ContentResDTO.ContentDetailRes result = ContentConverter.toContentDetailRes(
                content,
                "https://example.com/image.jpg",
                List.of("축제"),
                List.of(10L),
                false,
                null,
                List.of(),
                List.of()
        );

        assertThat(result.category()).isEqualTo(ContentCategory.FESTIVAL);
        assertThat(result.thumbnailImage()).isEqualTo("https://example.com/image.jpg");
        assertThat(result.hashtagIds()).containsExactly(10L);
    }
}
