package com.yeogido.backend.domain.region.controller;

import com.yeogido.backend.domain.region.dto.response.RegionResDTO;
import com.yeogido.backend.domain.region.service.RegionService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Region", description = "지역 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/regions")
public class RegionController {

    private final RegionService regionService;

    @Operation(
            summary = "상위 지역 목록 조회",
            description = "지역 선택 화면에서 사용하는 상위 지역 목록을 조회합니다."
    )
    @GetMapping
    public ApiResponse<RegionResDTO.RegionListResponse> getRegions() {
        RegionResDTO.RegionListResponse response = regionService.getRegions();

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(
            summary = "인기 지역 조회",
            description = "추천 코스 작성 화면에서 사용하는 인기 지역 목록을 조회합니다."
    )
    @GetMapping("/popular")
    public ApiResponse<List<RegionResDTO.PopularRegionRes>> getPopularRegions() {
        List<RegionResDTO.PopularRegionRes> response = regionService.getPopularRegions();

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}
