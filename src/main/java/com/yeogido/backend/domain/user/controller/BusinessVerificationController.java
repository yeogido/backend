package com.yeogido.backend.domain.user.controller;

import com.yeogido.backend.domain.user.dto.BusinessVerifyReqDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyResDTO;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자 API", description = "사용자 관련 API")
@RestController
@RequestMapping("/api/v1/users")
public class BusinessVerificationController {

    @Operation(
            summary = "소상공인 사업자 인증",
            description = "사업자 정보를 확인하고 소상공인 인증을 요청합니다")
    @PostMapping("/business-verify")
    public ApiResponse<BusinessVerifyResDTO> verifyBusiness(
            @Valid @RequestBody BusinessVerifyReqDTO request
    ) {
        BusinessVerifyResDTO response = BusinessVerifyResDTO.builder()
                .role(UserRole.BUSINESS)
                .businessInfoId(10L)
                .build();

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}
