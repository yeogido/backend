package com.yeogido.backend.domain.auth.controller;

import com.yeogido.backend.domain.auth.dto.*;
import com.yeogido.backend.domain.auth.service.AuthService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증 API", description = "회원가입, 로그인, 로그아웃, 토큰 재발급 및 비밀번호 재설정 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

  private final AuthService authService;

  @Operation(summary = "이메일 회원가입 API", description = "이메일과 비밀번호를 이용하여 회원가입을 진행합니다.")
  @PostMapping("/signup")
  public ApiResponse<SignUpResDTO> signUp(@Valid @RequestBody SignUpReqDTO request) {
    SignUpResDTO response = authService.signUp(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "이메일 로그인 API", description = "이메일과 비밀번호를 이용하여 로그인을 진행합니다.")
  @PostMapping("/login")
  public ApiResponse<TokenResDTO> login(@Valid @RequestBody LoginReqDTO request) {
    TokenResDTO response = authService.login(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "소셜 로그인 API", description = "소셜 제공자와 제공자 식별자를 이용하여 로그인 또는 자동 회원가입을 진행합니다.")
  @PostMapping("/social-login")
  public ApiResponse<TokenResDTO> socialLogin(@Valid @RequestBody SocialLoginReqDTO request) {
    TokenResDTO response = authService.socialLogin(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "이메일 중복 확인 API", description = "회원가입 전 이메일이 사용 가능한지 확인합니다.")
  @GetMapping("/check-email")
  public ApiResponse<EmailCheckResDTO> checkEmail(
    @RequestParam
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    String email
  ) {
    EmailCheckResDTO response = authService.checkEmail(email);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "로그아웃 API", description = "현재 로그인된 기기에서 인증 토큰을 무효화합니다.")
  @PostMapping("/logout")
  public ApiResponse<Void> logout(@RequestHeader("Authorization") String authorization) {
    authService.logout(authorization);
    return ApiResponse.onSuccess(SuccessCode.OK);
  }

  @Operation(summary = "비밀번호 찾기 인증번호 발송 API", description = "비밀번호를 재설정하려는 사용자의 이메일로 6자리 인증번호를 발송합니다.")
  @PostMapping("/password/send-code")
  public ApiResponse<Void> sendResetCode(@Valid @RequestBody PasswordSendCodeReqDTO request) {
    authService.sendResetCode(request);
    return ApiResponse.onSuccess(SuccessCode.OK);
  }

  @Operation(summary = "비밀번호 찾기 인증번호 검증 API", description = "이메일로 받은 인증번호가 유효한지 확인하고 비밀번호 재설정을 위한 임시 토큰을 발급합니다.")
  @PostMapping("/password/verify-code")
  public ApiResponse<PasswordVerifyCodeResDTO> verifyResetCode(@Valid @RequestBody PasswordVerifyCodeReqDTO request) {
    PasswordVerifyCodeResDTO response = authService.verifyResetCode(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }
}
