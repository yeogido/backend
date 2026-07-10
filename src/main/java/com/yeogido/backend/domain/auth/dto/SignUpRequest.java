package com.yeogido.backend.domain.auth.dto;

import com.yeogido.backend.domain.user.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SignUpRequest(
  @NotBlank(message = "이메일은 필수 입력값입니다.")
  @Email(message = "올바른 이메일 형식이어야 합니다.")
  String email,

  @NotBlank(message = "비밀번호는 필수 입력값입니다.")
  @Pattern(
    regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
    message = "비밀번호는 영문, 숫자, 특수문자를 포함하여 8~20자여야 합니다."
  )
  String password,

  @NotBlank(message = "닉네임은 필수 입력값입니다.")
  String nickname,

  @NotNull(message = "성별은 필수 입력값입니다.")
  Gender gender,

  @NotBlank(message = "생년은 필수 입력값입니다.")
  @Pattern(regexp = "^\\d{4}$", message = "생년은 4자리 숫자여야 합니다.")
  String birthYear,

  @NotNull(message = "지역 ID는 필수 입력값입니다.")
  Long regionId
) {}
