package com.yeogido.backend.domain.course.dto.request;

import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseSortType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public class CourseReqDTO {

    @Schema(name = "CourseListRequest", description = "추천 코스 목록 조회 요청")
    public record CourseListReq(

            @NotNull(message = "코스 타입은 필수입니다")
            @Schema(description = "코스 타입", example = "OFFICIAL")
            CourseType courseType,

            @Schema(description = "검색어", example = "강릉")
            String keyword,

            @Schema(description = "이동 수단", example = "CAR")
            TransportType transportType,

            @Schema(description = "여행 기간", example = "DAY_TRIP")
            DurationType durationType,

            @Schema(description = "동행 유형", example = "SOLO")
            CompanionType companionType,

            @Schema(description = "정렬 기준", example = "RECOMMEND")
            CourseSortType sort,

            @Schema(description = "커서 ID", example = "1")
            Long cursor,

            @Schema(description = "조회 개수", example = "20", defaultValue = "20")
            Integer size
    ) {
        public CourseListReq {
            if (size == null) {
                size = 20;
            }
        }
    }
}
