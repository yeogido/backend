package com.yeogido.backend.domain.terms;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Terms", description = "필수 약관 및 동의 API")
@RestController
@RequiredArgsConstructor
public class TermsController {
  private final TermsService service;

  @Operation(summary = "현재 필수 약관 조회", description = "시행된 최신 약관 두 개의 전문과 버전을 반환합니다. 로그인 없이 조회할 수 있습니다.")
  @GetMapping("/api/v1/terms")
  public ApiResponse<List<TermsDTO.Document>> current() {
    return ApiResponse.onSuccess(SuccessCode.OK, service.currentDocuments());
  }

  @Operation(summary = "내 필수 약관 동의 상태 조회")
  @GetMapping("/api/v1/users/me/terms")
  public ApiResponse<TermsDTO.Status> status(@AuthenticationPrincipal AuthUser user) {
    return ApiResponse.onSuccess(SuccessCode.OK, service.status(user.userId()));
  }

  @Operation(summary = "필수 약관 동의", description = "기존 회원의 최초 동의 및 개정 약관 동의. 동일 버전 재요청은 동의 시각을 변경하지 않습니다.")
  @PutMapping("/api/v1/users/me/terms")
  public ApiResponse<TermsDTO.Status> agree(@AuthenticationPrincipal AuthUser user,
                                          @Valid @RequestBody TermsDTO.Request request) {
    return ApiResponse.onSuccess(SuccessCode.OK, service.agree(user.userId(), request.agreements()));
  }
}
