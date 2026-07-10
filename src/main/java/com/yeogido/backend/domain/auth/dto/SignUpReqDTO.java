package com.yeogido.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(name = "SignUpReqDTO", description = "이메일 회원가입 요청")
public record SignUpReqDTO(
  @Schema(description = "이메일", example = "abc@example.com")
  @NotBlank(message = "이메일은 필수 입력값입니다.")
  @Email(message = "올바른 이메일 형식이어야 합니다.")
  String email,

  @Schema(description = "비밀번호", example = "password123!")
  @NotBlank(message = "비밀번호는 필수 입력값입니다.")
  @Pattern(
    regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
    message = "비밀번호는 영문, 숫자, 특수문자를 포함하여 8~20자여야 합니다."
  )
  String password,

  @Schema(description = "이름", example = "홍길동")
  @NotBlank(message = "이름은 필수 입력값입니다.")
  String name,

  @Schema(description = "닉네임", example = "길동이")
  @NotBlank(message = "닉네임은 필수 입력값입니다.")
  String nickname,

  @Schema(description = "지역", example = "서울")
  @NotBlank(message = "지역은 필수 입력값입니다.")
  String region
) {}
