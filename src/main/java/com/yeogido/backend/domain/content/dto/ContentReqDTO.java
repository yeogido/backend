package com.yeogido.backend.domain.content.dto;

import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentLinkType;
import com.yeogido.backend.domain.content.enums.ContentListStatus;
import com.yeogido.backend.domain.content.enums.ContentPublicationStatus;
import com.yeogido.backend.domain.content.enums.ContentSort;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

public class ContentReqDTO {

    public record ContentCalendarReq(
            @Schema(description = "조회 시작일. 월별 조회는 해당 월의 첫날, 하루 조회는 종료일과 같은 날짜를 전달합니다.", example = "2026-10-01")
            @NotNull(message = "조회 시작일은 필수입니다.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @Schema(description = "조회 종료일. 월별 조회는 해당 월의 말일을 전달하며 양 끝 날짜를 모두 포함합니다.", example = "2026-10-31")
            @NotNull(message = "조회 종료일은 필수입니다.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @Schema(description = "카테고리. 생략하면 전체 조회 (FESTIVAL: 행사, EXPERIENCE: 체험, EXHIBITION: 전시, PERFORMANCE: 공연)")
            ContentCategory category,

            @Schema(description = "정렬 기준 (RECOMMEND: 추천순, LIKE: 저장순, DISTANCE: 거리순, DEADLINE: 종료 임박순)", defaultValue = "RECOMMEND")
            ContentSort sort,

            @Schema(description = "선택한 지역 ID. 거리순에서 좌표가 없으면 이 지역 중심을 사용합니다.")
            @Positive
            Long regionId,

            @Schema(description = "거리순 기준 위도. longitude와 함께 전달합니다.")
            Double latitude,

            @Schema(description = "거리순 기준 경도. latitude와 함께 전달합니다.")
            Double longitude
    ) {
        public ContentCalendarReq(LocalDate startDate, LocalDate endDate) {
            this(startDate, endDate, null, null, null, null, null);
        }

        @Schema(hidden = true)
        @AssertTrue(message = "시작일은 종료일보다 늦을 수 없으며 날짜의 연도는 1~9999여야 합니다.")
        public boolean isValidSelection() {
            // 필수값 누락은 각 필드의 @NotNull에서 검증합니다.
            return startDate == null || endDate == null
                    || (!startDate.isAfter(endDate)
                    && startDate.getYear() >= 1 && endDate.getYear() <= 9999);
        }
    }

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

            @Schema(description = "게시 상태 필터. PENDING은 관리자만 조회할 수 있습니다.", defaultValue = "PUBLISHED")
            ContentPublicationStatus publicationStatus,

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
            @NotBlank(message = "문화콘텐츠 제목은 필수입니다.")
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

            @Valid
            @Schema(description = "공식 홈페이지와 SNS 등 외부 링크")
            List<ExternalLinkReq> officialLinks,

            @Schema(description = "대표 사진 Key")
            @NotBlank(message = "대표 이미지는 필수입니다.")
            String thumbnailImageKey,

            @Schema(description = "해시태그 ID 목록 (최대 5개)")
            @Size(max = 5, message = "해시태그는 최대 5개까지 선택할 수 있습니다.")
            List<Long> hashtagIds

    ){}

    @Schema(name = "ContentUpdateRequest", description = "문화콘텐츠 수정 요청")
    public record ContentUpdateReq(

            @Valid
            @Schema(description = "변경할 장소 정보. 생략하면 기존 장소를 유지합니다.")
            PlaceReq place,

            @Schema(description = "문화콘텐츠명. 생략하면 기존 값을 유지합니다.")
            @Pattern(regexp = ".*\\S.*", message = "문화콘텐츠 제목은 공백일 수 없습니다.")
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

            @Valid
            @Schema(description = "교체할 외부 링크 목록. 생략하면 기존 목록을 유지합니다.")
            List<ExternalLinkReq> officialLinks,

            @Schema(description = "새 대표 사진 Key. 생략하면 기존 이미지를 유지합니다.")
            String thumbnailImageKey,

            @Schema(description = "교체할 해시태그 ID 목록. 생략하면 기존 목록을 유지합니다. (최대 5개)")
            @Size(max = 5, message = "해시태그는 최대 5개까지 선택할 수 있습니다.")
            List<Long> hashtagIds

    ){}

    public record ExternalLinkReq(
            @Schema(description = "링크 유형", example = "INSTAGRAM")
            @NotNull
            ContentLinkType type,

            @Schema(description = "화면 표시명", example = "공식 인스타그램")
            String label,

            @Schema(description = "링크 URL")
            @NotBlank
            String url
    ) {}

    @Schema(name = "ContentPublishRequest", description = "동기화 콘텐츠 게시 요청")
    public record ContentPublishReq(

            @Schema(description = "수정할 문화콘텐츠명. 생략하면 동기화된 값을 유지합니다.")
            @Pattern(regexp = ".*\\S.*", message = "문화콘텐츠 제목은 공백일 수 없습니다.")
            String title,

            @Schema(description = "수정할 설명. 생략하면 동기화된 값을 유지합니다.")
            String description,

            @Schema(description = "수정할 카테고리. 생략하면 동기화된 값을 유지합니다.")
            ContentCategory category,

            @Schema(description = "교체할 해시태그 ID 목록. 생략하면 기존 목록을 유지합니다. (최대 5개)")
            @Size(max = 5, message = "해시태그는 최대 5개까지 선택할 수 있습니다.")
            List<Long> hashtagIds,

            @Schema(description = "추천 우선순위", example = "0")
            @PositiveOrZero
            Integer recommendPriority
    ) {}

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
