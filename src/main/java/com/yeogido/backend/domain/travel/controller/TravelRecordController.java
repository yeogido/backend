package com.yeogido.backend.domain.travel.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.service.TravelRecordService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "TravelRecord", description = "여행 기록 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/travel-records")
public class TravelRecordController {

    private final TravelRecordService travelRecordService;

    @Operation(
            summary = "내 여행 기록 목록 조회",
            description = "로그인한 사용자가 작성한 여행 기록 목록을 조회합니다."
    )
    @GetMapping
    public ApiResponse<CursorResponse<TravelRecordResDTO.TravelRecordSummary>> getMyTravelRecords(
            @AuthenticationPrincipal AuthUser authUser,
            @ParameterObject @ModelAttribute TravelRecordReqDTO.ListRequest request
    ) {
        CursorResponse<TravelRecordResDTO.TravelRecordSummary> result =
                travelRecordService.getMyTravelRecords(authUser.userId(), request);
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "내 여행 기록 연도 목록 조회",
            description = "로그인한 사용자가 작성한 여행 기록 중 실제 기록이 존재하는 연도 목록을 조회합니다."
    )
    @GetMapping("/years")
    public ApiResponse<TravelRecordResDTO.YearListResponse> getMyTravelRecordYears(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        TravelRecordResDTO.YearListResponse result = travelRecordService.getMyTravelRecordYears(
                authUser.userId()
        );
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "여행 기록 상세 조회",
            description = "선택한 여행 기록의 상세 정보를 조회합니다."
    )
    @GetMapping("/{travelRecordId}")
    public ApiResponse<TravelRecordResDTO.DetailResponse> getTravelRecord(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long travelRecordId
    ) {
        TravelRecordResDTO.DetailResponse result = travelRecordService.getTravelRecord(
                authUser.userId(),
                travelRecordId
        );
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "여행 기록 등록",
            description = "사용자가 여행 기간, 지역, 사진, 폴더 테마, 스티커 꾸미기 정보를 입력하여 새로운 여행 기록을 생성합니다."
    )
    @PostMapping
    public ApiResponse<TravelRecordResDTO.CreateResponse> createTravelRecord(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody TravelRecordReqDTO.CreateRequest request
    ) {
        TravelRecordResDTO.CreateResponse result = travelRecordService.createTravelRecord(
                authUser.userId(),
                request
        );
        return ApiResponse.onSuccess(SuccessCode.CREATED, result);
    }

    @Operation(
            summary = "여행 기록 수정",
            description = "로그인한 사용자가 본인이 작성한 여행 기록의 최종 상태를 저장합니다. 폴더 테마는 현재 BASIC으로 고정됩니다."
    )
    @PatchMapping("/{travelRecordId}")
    public ApiResponse<TravelRecordResDTO.UpdateResponse> updateTravelRecord(
            @PathVariable Long travelRecordId,
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody TravelRecordReqDTO.UpdateRequest request
    ) {
        TravelRecordResDTO.UpdateResponse result = travelRecordService.updateTravelRecord(
                travelRecordId,
                authUser.userId(),
                request
        );

        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "여행 기록 삭제",
            description = "로그인한 사용자가 본인이 작성한 여행 기록을 삭제합니다."
    )
    @DeleteMapping("/{travelRecordId}")
    public ApiResponse<Void> deleteTravelRecord(
            @PathVariable Long travelRecordId,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        travelRecordService.deleteTravelRecord(travelRecordId, authUser.userId());

        return ApiResponse.onSuccess(SuccessCode.OK);
    }
}
