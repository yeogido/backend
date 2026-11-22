package com.yeogido.backend.domain.travel.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordShareResDTO;
import com.yeogido.backend.domain.travel.service.TravelRecordShareService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "TravelRecord", description = "여행 기록 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/travel-records")
public class TravelRecordShareController {
    private final TravelRecordShareService service;

    @Operation(summary = "여행 폴더 공유 링크 발급", description = "로그인 필수. PUBLIC은 조회 사용자, FRIENDS는 생성자와 참여자만 발급합니다. "
            + "PRIVATE는 403입니다. 반환 token으로 프론트 공유 페이지 URL을 구성합니다. 공개범위 변경 시 이전 토큰은 무효화됩니다.")
    @PostMapping("/{travelRecordId}/share-link")
    public ApiResponse<TravelRecordShareResDTO.Link> createLink(@PathVariable Long travelRecordId,
                                                              @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.onSuccess(SuccessCode.OK, service.createLink(travelRecordId, user.userId()));
    }

    @Operation(summary = "공유 링크로 여행 폴더 조회", description = "로그인 필수. PUBLIC 및 현재 참여자는 detail, FRIENDS 비참여자는 "
            + "사진 URL 목록, 스티커, 지역, 기간을 preview로 반환합니다. canAccept가 true이면 수락, 거절 창을 표시합니다. "
            + "하단 사진의 블러는 프론트에서 적용합니다. 잘못되거나 삭제, 무효화된 토큰은 404입니다.")
    @GetMapping("/shared/{token}")
    public ApiResponse<TravelRecordShareResDTO.View> getSharedRecord(@PathVariable String token,
                                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.onSuccess(SuccessCode.OK, service.getSharedRecord(token, user.userId()));
    }

    @Operation(summary = "공유 링크 수락, 거절", description = "FRIENDS 링크를 가진 로그인 사용자 누구나 ACCEPTED, DECLINED로 응답합니다. "
            + "친구 관계와 지정 초대는 필요하지 않습니다. 수락 재요청은 중복 참여를 만들지 않습니다. "
            + "거절 이력은 새로 저장하지 않으며 기존 지정 초대가 있으면 상태를 동기화합니다. 참여자는 거절 대신 나가기 API를 사용합니다.")
    @PatchMapping("/shared/{token}")
    public ApiResponse<TravelRecordShareResDTO.Respond> respond(@PathVariable String token,
            @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody TravelRecordInvitationReqDTO.RespondRequest request) {
        return ApiResponse.onSuccess(SuccessCode.OK, service.respond(token, user.userId(), request));
    }
}
