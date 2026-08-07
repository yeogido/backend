package com.yeogido.backend.domain.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.domain.auth.service.JwtTokenProvider;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    String token = resolveToken(request);

    if (!StringUtils.hasText(token)) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      AuthUser authUser = jwtTokenProvider.parseAccessToken(token);
      validateActiveUser(authUser);

      UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        authUser,
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + authUser.role().name()))
      );
      SecurityContextHolder.getContext().setAuthentication(authentication);
      filterChain.doFilter(request, response);
    } catch (GeneralException e) {
      SecurityContextHolder.clearContext();
      writeFailureResponse(response, e);
    }
  }

  private void validateActiveUser(AuthUser authUser) {
    userRepository.findById(authUser.userId())
      .ifPresentOrElse(user -> {
        if (user.getStatus() == UserStatus.DELETED) {
          throw new GeneralException(UserErrorCode.USER_WITHDRAWN);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
          throw new GeneralException(AuthErrorCode.LOGIN_REQUIRED);
        }
      }, () -> {
        throw new GeneralException(AuthErrorCode.LOGIN_REQUIRED);
      });
  }

  private String resolveToken(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
      return null;
    }

    return authorization.substring(BEARER_PREFIX.length());
  }

  private void writeFailureResponse(HttpServletResponse response, GeneralException exception) throws IOException {
    response.setStatus(exception.getErrorCode().getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), ApiResponse.onFailure(exception.getErrorCode()));
  }
}
