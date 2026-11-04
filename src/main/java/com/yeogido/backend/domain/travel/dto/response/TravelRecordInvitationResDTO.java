package com.yeogido.backend.domain.travel.dto.response;

import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public class TravelRecordInvitationResDTO {

    @Schema(name = "TravelRecordInvitationCreateResponse", description = "공유 앨범 초대 생성 결과")
    public record CreateResponse(
            @Schema(description = "여행 기록 ID", example = "20") Long travelRecordId,
            @Schema(description = "요청한 대상 순서의 초대 목록") List<InvitationItem> invitations
    ) {
    }

    @Schema(name = "TravelRecordInvitationItem", description = "생성된 참여 대기 초대")
    public record InvitationItem(
            @Schema(description = "초대 ID", example = "101") Long invitationId,
            @Schema(description = "초대받은 사용자 ID", example = "2") Long inviteeUserId,
            @Schema(description = "초대 상태", example = "PENDING") TravelRecordInvitationStatus status,
            @Schema(description = "초대 생성 일시") LocalDateTime createdAt
    ) {
    }
}
