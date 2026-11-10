package com.yeogido.backend.domain.placeenrichment.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.placeenrichment.dto.PlaceOverviewApiReleaseReqDTO;
import com.yeogido.backend.domain.placeenrichment.service.PlaceOverviewApiAdminService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Place Overview Admin", description = "관리자용 장소 설명 외부 API 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/place-overview/api-operations")
public class PlaceOverviewApiAdminController {

    private final PlaceOverviewApiAdminService service;

    @Operation(summary = "외부 API 작업 차단 해제",
            description = "인증 키 등의 장애 원인 수정 후 호출할 경우 장소 오버뷰 생성을 5분 뒤 재시도 예약")
    @PostMapping("/release")
    public ApiResponse<Void> release(@AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PlaceOverviewApiReleaseReqDTO request) {
        service.release(authUser.userId(), request.provider(), request.operation());
        return ApiResponse.onSuccess(SuccessCode.OK);
    }
}
