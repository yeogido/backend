package com.yeogido.backend.domain.travel.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordInvitationResDTO;
import com.yeogido.backend.domain.travel.service.TravelRecordInvitationService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @Operation(summary = "공유 앨범 초대", description = "FRIENDS 앨범의 생성자와 현재 참여자가 실제 친구에게 참여 초대를 보냅니다. "
            + "전체 대상 검증 후 함께 저장하며 초대만으로 조회나 수정 권한을 부여하지 않습니다.")
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
