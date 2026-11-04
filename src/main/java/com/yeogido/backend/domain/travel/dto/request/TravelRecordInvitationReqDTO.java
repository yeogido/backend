package com.yeogido.backend.domain.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public class TravelRecordInvitationReqDTO {

    @Schema(name = "TravelRecordInvitationListRequest", description = "받은 공유 앨범 초대 목록 조회 요청")
    public record ListRequest(
            @Schema(description = "이전 응답의 cursorId. 첫 조회 시 생략", example = "101") Long cursor,
            @Schema(description = "조회할 초대 개수 (1~100)", example = "10", defaultValue = "10",
                    minimum = "1", maximum = "100") Integer size
    ) {
    }

    @Schema(name = "TravelRecordInvitationCreateRequest", description = "공유 앨범 초대 요청")
    public record CreateRequest(
            @Schema(description = "초대할 친구 ID 목록. 최소 1명이며 중복 불가", example = "[2, 3]")
            @NotEmpty(message = "초대 대상은 최소 1명 이상이어야 합니다.")
            List<@NotNull @Positive Long> inviteeUserIds
    ) {
    }
}
