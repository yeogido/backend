package com.yeogido.backend.domain.content.dto;

import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentSort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ContentReqDTO {

    public record ContentListReq(

            @Schema(description = "지역 ID")
            Long regionId,

            @Schema(description = "카테고리")
            ContentCategory category,

            @Schema(description = "검색어")
            String keyword,

            @Schema(description = "정렬 기준")
            ContentSort sort,

            @Schema(description = "위도")
            Double latitude,

            @Schema(description = "경도")
            Double longitude,

            @Schema(description = "커서 값")
            String cursorValue,

            @Schema(description = "커서 ID")
            Long cursorId,

            @Schema(description = "조회 개수")
            @Positive
            Integer size

    ){}

    public record ContentCreateReq(

            @Valid
            @Schema(description = "장소 정보")
            PlaceReq place,

            @Schema(description = "문화콘텐츠명")
            String title,

            @Schema(description = "문화콘텐츠 설명")
            String description,

            @Schema(description = "카테고리")
            String category,

            @Schema(description = "행사 시작일")
            LocalDate startDate,

            @Schema(description = "행사 종료일")
            LocalDate endDate,

            @Schema(description = "문의 연락처")
            String contactPhone,

            @Schema(description = "공식 홈페이지")
            String officialUrl,

            @Schema(description = "대표 사진 Key")
            String thumbnailImageKey,

            @Schema(description = "해시태그 ID 목록")
            List<Long> hashtagIds

    ){}

        public record PlaceReq(

                @Schema(description = "외부 장소 식별 ID")
                String externalPlaceId,

                @Schema(description = "장소 데이터 출처")
                String source,

                @Schema(description = "장소명")
                String name,

                @Schema(description = "도로명 주소")
                String roadAddress,

                @Schema(description = "지번 주소")
                String lotAddress,

                @Schema(description = "위도")
                BigDecimal latitude,

                @Schema(description = "경도")
                BigDecimal longitude

        ){}


}
