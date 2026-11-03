package com.yeogido.backend.domain.travel.dto.response;

import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TravelRecordInvitationResDTO {

    @Schema(name = "TravelRecordReceivedInvitation", description = "수신한 참여 대기 초대. 사진 정보는 제공하지 않음")
    public record ReceivedInvitation(
            @Schema(description = "초대 ID", example = "101") Long invitationId,
            @Schema(description = "초대 상태") TravelRecordInvitationStatus status,
            @Schema(description = "실제 초대한 사용자") TravelRecordResDTO.UserProfile inviter,
            @Schema(description = "앨범 생성자") TravelRecordResDTO.UserProfile owner,
            @Schema(description = "대기 폴더 표시용 기본 정보") PendingTravelRecord travelRecord,
            @Schema(description = "초대 생성 일시") LocalDateTime createdAt
    ) {
    }

    @Schema(name = "TravelRecordPendingInvitationAlbum", description = "수락 전 대기 폴더 기본 정보")
    public record PendingTravelRecord(
            @Schema(description = "여행 기록 ID", example = "20") Long travelRecordId,
            @Schema(description = "제목", example = "부산 감성 바다 여행") String title,
            @Schema(description = "지역 ID", example = "1") Long regionId,
            @Schema(description = "여행 시작일") LocalDate startDate,
            @Schema(description = "여행 종료일") LocalDate endDate,
            @Schema(description = "폴더 테마", example = "BASIC") FolderTheme folderTheme
    ) {
    }

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
