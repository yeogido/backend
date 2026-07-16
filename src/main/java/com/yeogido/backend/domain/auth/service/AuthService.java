package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.domain.auth.client.KakaoUserInfoClient;
import com.yeogido.backend.domain.auth.dto.AuthReqDTO;
import com.yeogido.backend.domain.auth.dto.AuthResDTO;
import com.yeogido.backend.domain.auth.dto.SocialSignupTokenPayload;
import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import com.yeogido.backend.domain.auth.entity.SocialAccount;
import com.yeogido.backend.domain.auth.enums.SocialProvider;
import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.domain.auth.repository.SocialAccountRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final KakaoUserInfoClient kakaoUserInfoClient;
  private final SocialAccountRepository socialAccountRepository;
  private final SocialSignupTokenService socialSignupTokenService;
  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;
  private final RegionRepository regionRepository;

  public AuthResDTO.SignUp signUp(AuthReqDTO.SignUp request) {
    return new AuthResDTO.SignUp(1L);
  }

  public AuthResDTO.Token login(AuthReqDTO.Login request) {
    return new AuthResDTO.Token(
      1L,
      "mock_access_token_for_" + request.email(),
      "mock_refresh_token_for_" + request.email()
    );
  }

  @Transactional(readOnly = true)
  public AuthResDTO.SocialLogin socialLogin(AuthReqDTO.SocialLogin request) {
    if (request.provider() != SocialProvider.KAKAO) {
      throw new GeneralException(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
    }

    SocialUserInfo socialUserInfo = kakaoUserInfoClient.getUserInfo(request.accessToken());

    if (!StringUtils.hasText(socialUserInfo.email())) {
      throw new GeneralException(AuthErrorCode.SOCIAL_EMAIL_REQUIRED);
    }

    return socialAccountRepository.findByProviderAndProviderId(
        socialUserInfo.provider(),
        socialUserInfo.providerId()
      )
      .map(this::createExistingSocialLoginResponse)
      .orElseGet(() -> createNewSocialLoginResponse(socialUserInfo));
  }

  private AuthResDTO.SocialLogin createExistingSocialLoginResponse(SocialAccount socialAccount) {
    User user = socialAccount.getUser();
    AuthResDTO.Token token = jwtTokenProvider.issueToken(user);

    return new AuthResDTO.SocialLogin(
      false,
      token.userId(),
      token.accessToken(),
      token.refreshToken(),
      null,
      null,
      null
    );
  }

  private AuthResDTO.SocialLogin createNewSocialLoginResponse(SocialUserInfo socialUserInfo) {
    String temporaryToken = socialSignupTokenService.issueToken(socialUserInfo);

    return new AuthResDTO.SocialLogin(
      true,
      null,
      null,
      null,
      temporaryToken,
      socialUserInfo.email(),
      socialUserInfo.name()
    );
  }

  @Transactional
  public AuthResDTO.Token completeSocialSignup(AuthReqDTO.SocialSignupComplete request) {
    SocialSignupTokenPayload payload = socialSignupTokenService.getPayload(request.temporaryToken());

    if (userRepository.existsByEmail(payload.email())) {
      throw new GeneralException(UserErrorCode.EMAIL_DUPLICATED);
    }

    Region region = regionRepository.findById(request.regionId())
      .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));

    User user = userRepository.save(User.builder()
      .nickname(request.name())
      .email(payload.email())
      .password(null)
      .gender(request.gender())
      .birthYear(request.birthYear())
      .region(region)
      .role(UserRole.USER)
      .status(UserStatus.ACTIVE)
      .profileImage(payload.profileImageUrl())
      .build());

    socialAccountRepository.save(SocialAccount.builder()
      .user(user)
      .provider(payload.provider())
      .providerId(payload.providerId())
      .build());

    socialSignupTokenService.deleteToken(request.temporaryToken());

    return jwtTokenProvider.issueToken(user);
  }

  public AuthResDTO.EmailCheck checkEmail(String email) {
    return new AuthResDTO.EmailCheck(true);
  }

  public void logout(String authorization) {
  }

  public void sendResetCode(AuthReqDTO.PasswordSendCode request) {
  }

  public AuthResDTO.PasswordVerifyCode verifyResetCode(AuthReqDTO.PasswordVerifyCode request) {
    return new AuthResDTO.PasswordVerifyCode("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  }
}
