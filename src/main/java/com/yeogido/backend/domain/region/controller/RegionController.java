package com.yeogido.backend.domain.region.controller;

import com.yeogido.backend.domain.region.dto.response.RegionResDTO;
import com.yeogido.backend.domain.region.service.RegionService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Region", description = "지역 관련 API")
@RestController
@Validated
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
            summary = "지역 검색",
            description = "keyword를 포함하는 지역을 Region.name 기준 LIKE 검색으로 조회합니다."
    )
    @GetMapping("/search")
    public ApiResponse<List<RegionResDTO.RegionSearchRes>> searchRegions(
            @Parameter(description = "검색 키워드", example = "강남", required = true)
            @RequestParam @NotBlank(message = "검색 키워드는 필수입니다.") String keyword
    ) {
        List<RegionResDTO.RegionSearchRes> response = regionService.searchRegions(keyword);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(
            summary = "지역 상세 조회",
            description = "지역 ID로 지역 상세 정보를 조회합니다."
    )
    @GetMapping("/{regionId}")
    public ApiResponse<RegionResDTO.RegionDetailRes> getRegion(
            @Parameter(description = "지역 ID", example = "1")
            @PathVariable Long regionId
    ) {
        RegionResDTO.RegionDetailRes response = regionService.getRegionDetail(regionId);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(
            summary = "하위 지역 목록 조회",
            description = "상위 지역을 선택했을 때 해당 지역의 하위 지역 목록을 조회합니다."
    )
    @GetMapping("/{regionId}/sub-regions")
    public ApiResponse<RegionResDTO.SubRegionListResponse> getSubRegions(
            @PathVariable Long regionId
    ) {
        RegionResDTO.SubRegionListResponse response = regionService.getSubRegions(regionId);

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
