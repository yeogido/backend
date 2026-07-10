package com.yeogido.backend.domain.auth.controller;

import com.yeogido.backend.domain.auth.dto.*;
import com.yeogido.backend.domain.auth.service.AuthService;
import com.yeogido.backend.global.common.ApiResponse;
import com.yeogido.backend.global.common.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증 API", description = "회원가입, 로그인, 로그아웃, 토큰 재발급 및 비밀번호 재설정 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @Operation(summary = "이메일 회원가입 API", description = "이메일과 비밀번호를 이용하여 회원가입을 진행합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping("/signup")
  public ApiResponse<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
    SignUpResponse response = authService.signUp(request);
    return ApiResponse.onSuccess(SuccessCode.CREATED, response);
  }

  @Operation(summary = "이메일 로그인 API", description = "이메일과 비밀번호를 이용하여 로그인을 진행합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    TokenResponse response = authService.login(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "소셜 로그인 API", description = "소셜 제공자(KAKAO, NAVER)와 OAuth 액세스 토큰을 이용하여 로그인을 진행합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "소셜 로그인 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping("/social-login")
  public ApiResponse<TokenResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
    TokenResponse response = authService.socialLogin(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "이메일 중복 확인 API", description = "회원가입 전 이메일이 사용 가능한지 확인합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이메일 중복 확인 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping("/check-email")
  public ApiResponse<EmailCheckResponse> checkEmail(@Valid @RequestBody EmailCheckRequest request) {
    EmailCheckResponse response = authService.checkEmail(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "로그아웃 API", description = "사용자의 리프레시 토큰을 서버에서 파기하여 로그아웃합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping("/logout")
  public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request);
    return ApiResponse.onSuccess(SuccessCode.OK);
  }

  @Operation(summary = "비밀번호 재설정 인증번호 발송 API", description = "비밀번호 재설정을 위해 입력된 이메일로 인증번호를 전송합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증번호 발송 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PatchMapping("/password/reset")
  public ApiResponse<Void> sendResetCode(@Valid @RequestBody PasswordResetCodeRequest request) {
    authService.sendResetCode(request);
    return ApiResponse.onSuccess(SuccessCode.OK);
  }

  @Operation(summary = "비밀번호 재설정 인증번호 검증 API", description = "이메일로 발송된 인증번호를 검증하여 일시적인 인증 토큰을 발급합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증번호 검증 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청")
  })
  @PostMapping("/password/verify-code")
  public ApiResponse<PasswordVerifyCodeResponse> verifyResetCode(@Valid @RequestBody PasswordVerifyCodeRequest request) {
    PasswordVerifyCodeResponse response = authService.verifyResetCode(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }
}
