package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.domain.auth.client.KakaoUserInfoClient;
import com.yeogido.backend.domain.auth.client.NaverUserInfoClient;
import com.yeogido.backend.domain.auth.converter.AuthConverter;
import com.yeogido.backend.domain.auth.dto.AuthReqDTO;
import com.yeogido.backend.domain.auth.dto.AuthResDTO;
import com.yeogido.backend.domain.auth.dto.SocialSignupTokenPayload;
import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import com.yeogido.backend.domain.auth.entity.SocialAccount;
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
public class AuthServiceImpl implements AuthService {

  private final KakaoUserInfoClient kakaoUserInfoClient;
  private final NaverUserInfoClient naverUserInfoClient;
  private final SocialAccountRepository socialAccountRepository;
  private final SocialSignupTokenService socialSignupTokenService;
  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;
  private final RegionRepository regionRepository;

  @Override
  public AuthResDTO.SignUp signUp(AuthReqDTO.SignUp request) {
    return new AuthResDTO.SignUp(1L);
  }

  @Override
  public AuthResDTO.Token login(AuthReqDTO.Login request) {
    return new AuthResDTO.Token(
      1L,
      "mock_access_token_for_" + request.email(),
      "mock_refresh_token_for_" + request.email()
    );
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResDTO.SocialLogin socialLogin(AuthReqDTO.SocialLogin request) {
    SocialUserInfo socialUserInfo = getSocialUserInfo(request);

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

  private SocialUserInfo getSocialUserInfo(AuthReqDTO.SocialLogin request) {
    return switch (request.provider()) {
      case KAKAO -> kakaoUserInfoClient.getUserInfo(request.accessToken());
      case NAVER -> naverUserInfoClient.getUserInfo(request.accessToken());
      default -> throw new GeneralException(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
    };
  }

  private AuthResDTO.SocialLogin createExistingSocialLoginResponse(SocialAccount socialAccount) {
    AuthResDTO.Token token = jwtTokenProvider.issueToken(socialAccount.getUser());
    return AuthConverter.toExistingSocialLoginResponse(token);
  }

  private AuthResDTO.SocialLogin createNewSocialLoginResponse(SocialUserInfo socialUserInfo) {
    String temporaryToken = socialSignupTokenService.issueToken(socialUserInfo);
    return AuthConverter.toNewSocialLoginResponse(temporaryToken, socialUserInfo);
  }

  @Override
  @Transactional
  public AuthResDTO.Token completeSocialSignup(AuthReqDTO.SocialSignupComplete request) {
    SocialSignupTokenPayload payload = socialSignupTokenService.getPayload(request.temporaryToken());

    if (socialAccountRepository.existsByProviderAndProviderId(payload.provider(), payload.providerId())) {
      throw new GeneralException(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS);
    }

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

  @Override
  public AuthResDTO.EmailCheck checkEmail(String email) {
    return new AuthResDTO.EmailCheck(true);
  }

  @Override
  public void logout(String authorization) {
  }

  @Override
  public void sendResetCode(AuthReqDTO.PasswordSendCode request) {
  }

  @Override
  public AuthResDTO.PasswordVerifyCode verifyResetCode(AuthReqDTO.PasswordVerifyCode request) {
    return new AuthResDTO.PasswordVerifyCode("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  }
}
