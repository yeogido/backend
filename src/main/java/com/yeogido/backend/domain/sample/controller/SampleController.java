package com.yeogido.backend.domain.sample.controller;

import com.yeogido.backend.domain.sample.dto.request.SampleRequest;
import com.yeogido.backend.domain.sample.dto.response.SampleResponse;
import com.yeogido.backend.domain.sample.service.SampleService;
import com.yeogido.backend.global.common.ApiResponse;
import com.yeogido.backend.global.common.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Sample",
        description = "프로젝트 구조 예제 API"
)
@RestController
@RequestMapping("/api/v1/sample")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    @Operation(
            summary = "Sample 조회",
            description = "Sample을 조회하는 예제 API입니다."
    )
    @GetMapping("/{sampleId}")
    public ApiResponse<SampleResponse.Detail> getSample(
            @PathVariable Long sampleId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                sampleService.getSample(sampleId)
        );
    }

    @Operation(
            summary = "Sample Validation 테스트",
            description = "Validation 예외 응답을 확인하는 예제 API입니다."
    )
    @PostMapping("/validation")
    public ApiResponse<Void> validateSample(
            @Valid @RequestBody SampleRequest.Create request
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK);
    }
}
