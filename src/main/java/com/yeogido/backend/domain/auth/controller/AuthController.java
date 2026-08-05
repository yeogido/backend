package com.yeogido.backend.domain.auth.controller;

import com.yeogido.backend.domain.auth.dto.AuthReqDTO;
import com.yeogido.backend.domain.auth.dto.AuthResDTO;
import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.auth.service.AuthService;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
  public ApiResponse<AuthResDTO.SignUp> signUp(@Valid @RequestBody AuthReqDTO.SignUp request) {
    AuthResDTO.SignUp response = authService.signUp(request);
    return ApiResponse.onSuccess(SuccessCode.CREATED, response);
  }

  @Operation(summary = "이메일 로그인 API", description = "이메일과 비밀번호를 이용하여 로그인을 진행합니다.")
  @PostMapping("/login")
  public ApiResponse<AuthResDTO.Token> login(@Valid @RequestBody AuthReqDTO.Login request) {
    AuthResDTO.Token response = authService.login(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "소셜 로그인 API", description = "프론트에서 전달받은 소셜 accessToken을 검증하고, 신규 사용자는 프로필 작성 단계로 안내합니다.")
  @PostMapping("/social-login")
  public ApiResponse<AuthResDTO.SocialLogin> socialLogin(@Valid @RequestBody AuthReqDTO.SocialLogin request) {
    AuthResDTO.SocialLogin response = authService.socialLogin(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "소셜 로그인 추가 프로필 작성 완료 API", description = "신규 소셜 사용자의 추가 프로필 정보를 저장하고 회원가입을 완료합니다.")
  @PostMapping("/social-signup/complete")
  public ApiResponse<AuthResDTO.Token> completeSocialSignup(@Valid @RequestBody AuthReqDTO.SocialSignupComplete request) {
    AuthResDTO.Token response = authService.completeSocialSignup(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "이메일 중복 확인 API", description = "회원가입 전 이메일이 사용 가능한지 확인합니다.")
  @GetMapping("/check-email")
  public ApiResponse<AuthResDTO.EmailCheck> checkEmail(
    @RequestParam
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    String email
  ) {
    AuthResDTO.EmailCheck response = authService.checkEmail(email);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "로그아웃 API", description = "인증된 사용자의 Refresh Token을 삭제하여 토큰 재발급을 차단합니다.")
  @PostMapping("/logout")
  public ApiResponse<Void> logout(@AuthenticationPrincipal AuthUser authUser) {
    authService.logout(authUser);
    return ApiResponse.onSuccess(SuccessCode.OK);
  }

  @Operation(summary = "토큰 재발급 API", description = "Refresh Token을 검증하고 새로운 Access Token과 Refresh Token을 발급합니다.")
  @PostMapping("/reissue")
  public ApiResponse<AuthResDTO.Token> reissue(@Valid @RequestBody AuthReqDTO.Reissue request) {
    AuthResDTO.Token response = authService.reissue(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "비밀번호 찾기 인증번호 발송 API", description = "비밀번호를 재설정하려는 사용자의 이메일로 6자리 인증번호를 발송합니다.")
  @PostMapping("/password/send-code")
  public ApiResponse<Void> sendResetCode(@Valid @RequestBody AuthReqDTO.PasswordSendCode request) {
    authService.sendResetCode(request);
    return ApiResponse.onSuccess(SuccessCode.OK);
  }

  @Operation(summary = "비밀번호 찾기 인증번호 검증 API", description = "이메일로 받은 인증번호가 유효한지 확인하고 비밀번호 재설정을 위한 임시 토큰을 발급합니다.")
  @PostMapping("/password/verify-code")
  public ApiResponse<AuthResDTO.PasswordVerifyCode> verifyResetCode(@Valid @RequestBody AuthReqDTO.PasswordVerifyCode request) {
    AuthResDTO.PasswordVerifyCode response = authService.verifyResetCode(request);
    return ApiResponse.onSuccess(SuccessCode.OK, response);
  }

  @Operation(summary = "비밀번호 재설정 API", description = "비밀번호 재설정 임시 토큰을 검증하고 새 비밀번호로 변경합니다.")
  @PatchMapping("/password/reset")
  public ApiResponse<Void> resetPassword(@Valid @RequestBody AuthReqDTO.PasswordReset request) {
    authService.resetPassword(request);
    return ApiResponse.onSuccess(SuccessCode.OK);
  }
}
