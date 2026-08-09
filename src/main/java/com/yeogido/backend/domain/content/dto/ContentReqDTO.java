package com.yeogido.backend.domain.content.dto;

import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentListStatus;
import com.yeogido.backend.domain.content.enums.ContentSort;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ContentReqDTO {

    public record ContentListReq(

            @Schema(description = "지역 ID")
            Long regionId,

            @Schema(
                    description = "카테고리 필터"
            )
            ContentCategory category,

            @Schema(
                    description = "행사 상태 목록 (UPCOMING: 진행 예정, ONGOING: 진행 중, ENDED: 종료). 여러 상태 선택 가능",
                    defaultValue = "[UPCOMING, ONGOING]"
            )
            List<ContentListStatus> statuses,

            @Schema(description = "검색어")
            String keyword,

            @Schema(
                    description = "서버 정렬 기준",
                    defaultValue = "RECOMMEND"
            )
            ContentSort sort,

            @Schema(description = "거리순 정렬 기준 위도")
            Double latitude,

            @Schema(description = "거리순 정렬 기준 경도")
            Double longitude,

            @Schema(description = "이전 응답의 cursorValue")
            String cursorValue,

            @Schema(description = "이전 응답의 cursorId")
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
            ContentCategory category,

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

    @Schema(name = "ContentUpdateRequest", description = "문화콘텐츠 수정 요청")
    public record ContentUpdateReq(

            @Valid
            @Schema(description = "변경할 장소 정보. 생략하면 기존 장소를 유지합니다.")
            PlaceReq place,

            @Schema(description = "문화콘텐츠명. 생략하면 기존 값을 유지합니다.")
            String title,

            @Schema(description = "문화콘텐츠 설명. 생략하면 기존 값을 유지합니다.")
            String description,

            @Schema(description = "카테고리. 생략하면 기존 값을 유지합니다.")
            ContentCategory category,

            @Schema(description = "행사 시작일. 생략하면 기존 값을 유지합니다.")
            LocalDate startDate,

            @Schema(description = "행사 종료일. 생략하면 기존 값을 유지합니다.")
            LocalDate endDate,

            @Schema(description = "문의 연락처. 생략하면 기존 값을 유지합니다.")
            String contactPhone,

            @Schema(description = "공식 홈페이지. 생략하면 기존 값을 유지합니다.")
            String officialUrl,

            @Schema(description = "새 대표 사진 Key. 생략하면 기존 이미지를 유지합니다.")
            String thumbnailImageKey,

            @Schema(description = "교체할 해시태그 ID 목록. 생략하면 기존 목록을 유지합니다.")
            List<Long> hashtagIds

    ){}

    public record PlaceReq(

                @Schema(description = "외부 장소 식별 ID")
                String externalPlaceId,

                @Schema(description = "장소 데이터 출처")
                PlaceSource source,

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
