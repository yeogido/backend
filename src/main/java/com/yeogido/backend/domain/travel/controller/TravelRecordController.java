package com.yeogido.backend.domain.travel.controller;

import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.service.TravelRecordService;
import com.yeogido.backend.global.common.ApiResponse;
import com.yeogido.backend.global.common.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<TravelRecordResDTO.ListResponse> getMyTravelRecords(
            @ModelAttribute TravelRecordReqDTO.ListRequest request
    ) {
        TravelRecordResDTO.ListResponse result = travelRecordService.getMyTravelRecords(request);
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "여행 기록 상세 조회",
            description = "선택한 여행 기록의 상세 정보를 조회합니다."
    )
    @GetMapping("/{travelRecordId}")
    public ApiResponse<TravelRecordResDTO.DetailResponse> getTravelRecord(
            @PathVariable Long travelRecordId
    ) {
        TravelRecordResDTO.DetailResponse result = travelRecordService.getTravelRecord(travelRecordId);
        return ApiResponse.onSuccess(SuccessCode.OK, result);
    }

    @Operation(
            summary = "여행 기록 등록",
            description = "사용자가 여행 기간, 지역, 사진, 폴더 테마, 스티커 꾸미기 정보를 입력하여 새로운 여행 기록을 생성합니다."
    )
    @PostMapping
    public ApiResponse<TravelRecordResDTO.CreateResponse> createTravelRecord(
            @Valid @RequestBody TravelRecordReqDTO.CreateRequest request
    ) {
        TravelRecordResDTO.CreateResponse result = travelRecordService.createTravelRecord(request);
        return ApiResponse.onSuccess(SuccessCode.CREATED, result);
    }
}
