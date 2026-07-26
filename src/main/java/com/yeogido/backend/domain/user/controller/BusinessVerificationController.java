package com.yeogido.backend.domain.user.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.user.dto.BusinessInfoResDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyReqDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyResDTO;
import com.yeogido.backend.domain.user.service.BusinessVerificationService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "사용자 API", description = "사용자 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class BusinessVerificationController {

    private final BusinessVerificationService businessVerificationService;

    @Operation(
            summary = "소상공인 사업자 인증",
            description = "사업자 정보를 확인하고 소상공인 인증을 완료합니다"
    )
    @PostMapping("/business-verify")
    public ApiResponse<BusinessVerifyResDTO> verifyBusiness(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody BusinessVerifyReqDTO request
    ) {
        BusinessVerifyResDTO response =
                businessVerificationService.verifyBusiness(authUser.userId(), request);

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }

    @Operation(
            summary = "인증 사업장 목록 조회",
            description = "로그인한 사용자가 인증한 사업장 목록을 조회합니다."
    )
    @GetMapping("/me/businesses")
    public ApiResponse<List<BusinessInfoResDTO>> getApprovedBusinesses(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        List<BusinessInfoResDTO> response =
                businessVerificationService.getApprovedBusinesses(authUser.userId());

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}