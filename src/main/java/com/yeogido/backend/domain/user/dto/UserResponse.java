package com.yeogido.backend.domain.user.dto;

import com.yeogido.backend.domain.user.enums.Gender;

public record UserResponse(
  Long userId,
  String email,
  String nickname,
  Gender gender,
  String birthYear,
  String regionName,
  String profileImage
) {}
