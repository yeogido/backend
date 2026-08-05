package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.domain.auth.client.KakaoUserInfoClient;
import com.yeogido.backend.domain.auth.client.KakaoTokenClient;
import com.yeogido.backend.domain.auth.client.NaverUserInfoClient;
import com.yeogido.backend.domain.auth.converter.AuthConverter;
import com.yeogido.backend.domain.auth.dto.AuthReqDTO;
import com.yeogido.backend.domain.auth.dto.AuthResDTO;
import com.yeogido.backend.domain.auth.dto.SocialSignupTokenPayload;
import com.yeogido.backend.domain.auth.dto.SocialUserInfo;
import com.yeogido.backend.domain.auth.entity.SocialAccount;
import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.domain.auth.repository.SocialAccountRepository;
import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.S3Service;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final KakaoUserInfoClient kakaoUserInfoClient;
  private final KakaoTokenClient kakaoTokenClient;
  private final NaverUserInfoClient naverUserInfoClient;
  private final SocialAccountRepository socialAccountRepository;
  private final SocialSignupTokenService socialSignupTokenService;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenService refreshTokenService;
  private final UserRepository userRepository;
  private final RegionRepository regionRepository;
  private final PasswordEncoder passwordEncoder;
  private final S3Service s3Service;
  private final PasswordResetCodeService passwordResetCodeService;
  private final MailService mailService;

  @Override
  @Transactional
  public AuthResDTO.SignUp signUp(AuthReqDTO.SignUp request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new GeneralException(UserErrorCode.EMAIL_DUPLICATED);
    }

    Region region = regionRepository.findById(request.regionId())
      .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));

    User user;
    try {
      user = userRepository.saveAndFlush(User.builder()
        .nickname(request.nickname())
        .email(request.email())
        .password(passwordEncoder.encode(request.password()))
        .gender(request.gender())
        .birthYear(request.birthYear())
        .region(region)
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .profileImage(null)
        .build());
    } catch (DataIntegrityViolationException e) {
      throw new GeneralException(UserErrorCode.EMAIL_DUPLICATED);
    }

    return new AuthResDTO.SignUp(user.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResDTO.Token login(AuthReqDTO.Login request) {
    User user = userRepository.findByEmail(request.email())
      .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
      .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

    if (!StringUtils.hasText(user.getPassword())
      || !passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new GeneralException(AuthErrorCode.PASSWORD_MISMATCH);
    }

    return issueAndSaveToken(user);
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
      case KAKAO -> kakaoUserInfoClient.getUserInfo(getKakaoAccessToken(request));
      case NAVER -> naverUserInfoClient.getUserInfo(getNaverAccessToken(request));
      default -> throw new GeneralException(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
    };
  }

  private String getKakaoAccessToken(AuthReqDTO.SocialLogin request) {
    if (!StringUtils.hasText(request.authorizationCode()) || !StringUtils.hasText(request.redirectUri())) {
      throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_LOGIN_REQUEST);
    }

    return kakaoTokenClient.getAccessToken(request.authorizationCode(), request.redirectUri());
  }

  private String getNaverAccessToken(AuthReqDTO.SocialLogin request) {
    if (!StringUtils.hasText(request.accessToken())) {
      throw new GeneralException(AuthErrorCode.INVALID_SOCIAL_LOGIN_REQUEST);
    }

    return request.accessToken();
  }

  private AuthResDTO.SocialLogin createExistingSocialLoginResponse(SocialAccount socialAccount) {
    AuthResDTO.Token token = issueAndSaveToken(socialAccount.getUser());
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

    String profileImageKey = s3Service.uploadImageFromUrl(payload.profileImageUrl(), ImageDirectory.PROFILE);

    User user = userRepository.save(User.builder()
      .nickname(request.name())
      .email(payload.email())
      .password(null)
      .gender(request.gender())
      .birthYear(request.birthYear())
      .region(region)
      .role(UserRole.USER)
      .status(UserStatus.ACTIVE)
      .profileImage(profileImageKey)
      .build());

    socialAccountRepository.save(SocialAccount.builder()
      .user(user)
      .provider(payload.provider())
      .providerId(payload.providerId())
      .build());

    socialSignupTokenService.deleteToken(request.temporaryToken());

    return issueAndSaveToken(user);
  }

  @Override
  @Transactional(readOnly = true)
  public AuthResDTO.EmailCheck checkEmail(String email) {
    return new AuthResDTO.EmailCheck(!userRepository.existsByEmail(email));
  }

  @Override
  public void logout(Long userId) {
    refreshTokenService.delete(userId);
  }

  @Override
  @Transactional
  public AuthResDTO.Token reissue(AuthReqDTO.Reissue request) {
    AuthUser authUser = jwtTokenProvider.parseRefreshToken(request.refreshToken());

    User user = userRepository.findById(authUser.userId())
      .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
      .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

    AuthResDTO.Token token = jwtTokenProvider.issueToken(user);
    if (!refreshTokenService.rotate(user.getId(), request.refreshToken(), token.refreshToken())) {
      throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    return token;
  }

  private AuthResDTO.Token issueAndSaveToken(User user) {
    AuthResDTO.Token token = jwtTokenProvider.issueToken(user);
    refreshTokenService.save(user.getId(), token.refreshToken());
    return token;
  }

  @Override
  @Transactional(readOnly = true)
  public void sendResetCode(AuthReqDTO.PasswordSendCode request) {
    User user = userRepository.findByEmail(request.email())
      .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
      .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

    String authCode = passwordResetCodeService.issueCode(user.getEmail());
    mailService.sendPasswordResetCode(user.getEmail(), authCode);
  }

  @Override
  public AuthResDTO.PasswordVerifyCode verifyResetCode(AuthReqDTO.PasswordVerifyCode request) {
    String resetToken = passwordResetCodeService.verifyCodeAndIssueResetToken(
      request.email(),
      request.authCode()
    );

    return new AuthResDTO.PasswordVerifyCode(resetToken);
  }

  @Override
  @Transactional
  public void resetPassword(AuthReqDTO.PasswordReset request) {
    String email = passwordResetCodeService.verifyResetTokenAndDelete(request.resetToken());

    User user = userRepository.findByEmail(email)
      .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
      .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

    user.updatePassword(passwordEncoder.encode(request.newPassword()));
  }
}
