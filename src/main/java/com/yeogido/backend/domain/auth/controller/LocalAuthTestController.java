package com.yeogido.backend.domain.auth.controller;

import com.yeogido.backend.domain.auth.dto.AuthResDTO;
import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.auth.service.JwtTokenProvider;
import com.yeogido.backend.domain.auth.service.RefreshTokenService;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.code.SuccessCode;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.config.LocalDataInitializer;
import com.yeogido.backend.global.exception.GeneralException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Local Auth Test", description = "로컬 인증 테스트 API")
@Profile("local")
@RestController
@RequestMapping("/api/v1/auth/test")
@RequiredArgsConstructor
public class LocalAuthTestController {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Operation(summary = "로컬 테스트용 토큰 발급 API", description = "local 프로필에서만 사용할 수 있는 테스트 사용자 Access/Refresh Token을 발급합니다.")
    @PostMapping("/token")
    public ApiResponse<AuthResDTO.Token> issueTestToken() {
        User user = userRepository.findByEmail(LocalDataInitializer.TEST_USER_EMAIL)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        AuthResDTO.Token response = jwtTokenProvider.issueToken(user);
        AuthUser authUser = jwtTokenProvider.parseRefreshToken(response.refreshToken());
        refreshTokenService.save(user.getId(), authUser.sessionId(), response.refreshToken());

        return ApiResponse.onSuccess(SuccessCode.OK, response);
    }
}
