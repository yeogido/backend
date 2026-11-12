package com.yeogido.backend.domain.travel.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordInvitationResDTO;
import com.yeogido.backend.domain.travel.service.TravelRecordInvitationService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "TravelRecord", description = "여행 기록 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/travel-records")
public class TravelRecordInvitationController {

    private final TravelRecordInvitationService invitationService;

    @Operation(summary = "공유 앨범 초대 수락, 거절", description = "본인이 받은 PENDING 초대에 응답합니다. "
            + "수락하면 참여 관계가 생성되며, 같은 응답의 재요청은 현재 상태가 유효하면 성공합니다.")
    @PatchMapping("/invitations/{invitationId}")
    public ApiResponse<TravelRecordInvitationResDTO.RespondResponse> respondToInvitation(
            @PathVariable Long invitationId,
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody TravelRecordInvitationReqDTO.RespondRequest request
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK,
                invitationService.respondToInvitation(invitationId, authUser.userId(), request));
    }

    @Operation(summary = "받은 공유 앨범 초대 목록 조회", description = "본인이 받은 PENDING 초대를 생성일, ID 내림차순으로 조회합니다. "
            + "수락 전 사진 정보는 제공하지 않으며 cursor에는 이전 응답의 cursorId를 전달합니다.")
    @GetMapping("/invitations")
    public ApiResponse<CursorResponse<TravelRecordInvitationResDTO.ReceivedInvitation>> getReceivedInvitations(
            @AuthenticationPrincipal AuthUser authUser,
            @ParameterObject @ModelAttribute TravelRecordInvitationReqDTO.ListRequest request
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK,
                invitationService.getReceivedInvitations(authUser.userId(), request));
    }

    @Operation(summary = "공유 앨범 초대", description = "PUBLIC 앨범의 생성자와 FRIENDS 앨범의 생성자 및 현재 참여자가 친구에게 초대를 보냅니다. "
            + "PUBLIC 앨범은 초대가 저장될 때 FRIENDS로 전환됩니다. 수락한 친구에게 조회 및 수정 권한이 부여됩니다.")
    @PostMapping("/{travelRecordId}/invitations")
    public ApiResponse<TravelRecordInvitationResDTO.CreateResponse> createInvitations(
            @PathVariable Long travelRecordId,
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody TravelRecordInvitationReqDTO.CreateRequest request
    ) {
        return ApiResponse.onSuccess(SuccessCode.CREATED,
                invitationService.createInvitations(travelRecordId, authUser.userId(), request));
    }
}
