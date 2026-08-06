package com.yeogido.backend.domain.auth.security;

import com.yeogido.backend.domain.user.enums.UserRole;

public record AuthUser(
  Long userId,
  UserRole role,
  String sessionId
) {
}
