package com.yeogido.backend.domain.friend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class FriendReqDTO {

    @Schema(name = "FriendRequestSendReq", description = "친구 요청 보내기 요청")
    public record SendRequest(
            @Schema(description = "상대방 친구코드 (앞뒤 공백 제거 후 대문자로 처리)", example = "K7M2QX9A")
            @NotBlank(message = "친구코드는 필수입니다.")
            String friendCode
    ) {
    }

    @Schema(name = "FriendTravelMapReq", description = "친구 여행 지도 조회 요청")
    public record TravelMapRequest(
            @Schema(description = "이전 응답의 cursorId", example = "20")
            @Positive(message = "cursor는 양수여야 합니다.")
            Long cursor,

            @Schema(description = "조회할 앨범 개수 (1~100)", example = "10", defaultValue = "10",
                    minimum = "1", maximum = "100")
            @Positive(message = "size는 양수여야 합니다.")
            Integer size,

            @Schema(description = "여행 종료 연도. 미전달 시 전체 연도", example = "2026")
            @Positive(message = "year는 양수여야 합니다.")
            @Max(value = 9999, message = "year는 9999 이하여야 합니다.")
            Integer year
    ) {
    }
}