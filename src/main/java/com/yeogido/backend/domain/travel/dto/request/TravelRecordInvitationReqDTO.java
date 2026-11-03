package com.yeogido.backend.domain.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public class TravelRecordInvitationReqDTO {

    @Schema(name = "TravelRecordInvitationCreateRequest", description = "공유 앨범 초대 요청")
    public record CreateRequest(
            @Schema(description = "초대할 친구 ID 목록. 최소 1명이며 중복 불가", example = "[2, 3]")
            @NotEmpty(message = "초대 대상은 최소 1명 이상이어야 합니다.")
            List<@NotNull @Positive Long> inviteeUserIds
    ) {
    }
}
