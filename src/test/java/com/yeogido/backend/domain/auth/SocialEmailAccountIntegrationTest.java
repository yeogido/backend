package com.yeogido.backend.domain.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.auth.client.*;
import com.yeogido.backend.domain.auth.dto.*;
import com.yeogido.backend.domain.auth.enums.SocialProvider;
import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.domain.auth.service.*;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.service.FriendCodeGenerator;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.terms.dto.TermsReqDTO;
import com.yeogido.backend.domain.terms.entity.TermsDocument;
import com.yeogido.backend.domain.terms.enums.TermsType;
import com.yeogido.backend.domain.terms.service.TermsServiceImpl;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.*;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.global.config.JpaConfig;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, TermsServiceImpl.class, AuthServiceImpl.class, JwtTokenProvider.class, ObjectMapper.class})
@TestPropertySource(properties = {
  "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
  "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
  "app.jwt.secret=test-only-signing-key"
})
class SocialEmailAccountIntegrationTest {
  private static final String EMAIL = "shared@example.com";
  @Autowired AuthServiceImpl auth;
  @Autowired TestEntityManager em;
  @MockitoBean KakaoUserInfoClient kakao;
  @MockitoBean KakaoTokenClient kakaoToken;
  @MockitoBean NaverUserInfoClient naver;
  @MockitoBean SocialSignupTokenService temporaryTokens;
  @MockitoBean RefreshTokenService refreshTokens;
  @MockitoBean PasswordEncoder passwords;
  @MockitoBean S3Service images;
  @MockitoBean PasswordResetCodeService resetCodes;
  @MockitoBean EmailVerificationService emailVerification;
  @MockitoBean MailService mail;
  @MockitoBean FriendCodeGenerator friendCodes;
  private Region region;

  @BeforeEach
  void setUp() {
    region = em.persist(Region.builder().name("서울").type(RegionType.REGION).build());
    for (var type : TermsType.values()) {
      em.persist(TermsDocument.builder().type(type).version("1.0").title("테스트 약관")
        .content("테스트 전용").effectiveAt(LocalDateTime.now().minusDays(1)).build());
    }
    when(friendCodes.generateUnique()).thenAnswer(invocation -> FriendCodeGenerator.randomCode());
    when(passwords.encode(anyString())).thenReturn("encoded");
    when(passwords.matches("Password123!", "encoded")).thenReturn(true);
  }

  @ParameterizedTest
  @EnumSource(value = SocialProvider.class, names = {"KAKAO", "NAVER"})
  void sameEmailCreatesSeparateUsersRegardlessOfSignupOrder(SocialProvider first) {
    var second = first == SocialProvider.KAKAO ? SocialProvider.NAVER : SocialProvider.KAKAO;
    var firstToken = signupSocial(first);
    var secondToken = signupSocial(second);
    em.flush();
    em.clear();
    assertThat(firstToken.userId()).isNotEqualTo(secondToken.userId());
    assertThat(em.find(User.class, firstToken.userId()).getAccountType()).isEqualTo(AccountType.SOCIAL);
    assertThat(em.find(User.class, secondToken.userId()).getAccountType()).isEqualTo(AccountType.SOCIAL);
    assertThat(loginSocial(first).userId()).isEqualTo(firstToken.userId());
    assertThat(loginSocial(second).userId()).isEqualTo(secondToken.userId());
    assertThat(loginSocial(first).isNewUser()).isFalse();
    assertThat(auth.checkEmail(EMAIL).isAvailable()).isTrue();
  }

  @Test
  void emailSignupLoginAndPasswordResetOnlyTargetEmailAccount() {
    var kakaoUser = signupSocial(SocialProvider.KAKAO);
    var naverUser = signupSocial(SocialProvider.NAVER);
    auth.sendEmailVerificationCode(new AuthReqDTO.EmailSendCode(EMAIL));
    verify(emailVerification).issueCode(EMAIL);
    var emailUser = auth.signUp(emailSignup());
    assertThat(auth.login(new AuthReqDTO.Login(EMAIL, "Password123!")).userId()).isEqualTo(emailUser.userId());
    assertThat(auth.checkEmail(EMAIL).isAvailable()).isFalse();
    when(resetCodes.verifyResetTokenAndDelete("reset")).thenReturn(EMAIL);
    when(passwords.encode("NewPassword123!")).thenReturn("new-encoded");
    auth.sendResetCode(new AuthReqDTO.PasswordSendCode(EMAIL));
    verify(resetCodes).issueCode(EMAIL);
    auth.resetPassword(new AuthReqDTO.PasswordReset("reset", "NewPassword123!"));
    em.flush();
    em.clear();
    assertThat(em.find(User.class, emailUser.userId()).getPassword()).isEqualTo("new-encoded");
    assertThat(em.find(User.class, kakaoUser.userId()).getPassword()).isNull();
    assertThat(em.find(User.class, naverUser.userId()).getPassword()).isNull();
  }

  @Test
  void socialSignupAlsoWorksAfterEmailSignupAndRejectsSameSocialIdentity() {
    var emailUser = auth.signUp(emailSignup());
    var kakaoUser = signupSocial(SocialProvider.KAKAO);
    assertThat(kakaoUser.userId()).isNotEqualTo(emailUser.userId());
    assertThatThrownBy(() -> signupSocial(SocialProvider.KAKAO))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS));
    assertThatThrownBy(() -> auth.signUp(emailSignup()))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.EMAIL_DUPLICATED));
  }

  @Test
  void socialOnlyEmailCannotLoginOrResetPasswordAsEmailAccount() {
    signupSocial(SocialProvider.NAVER);
    assertThatThrownBy(() -> auth.login(new AuthReqDTO.Login(EMAIL, "Password123!")))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    assertThatThrownBy(() -> auth.sendResetCode(new AuthReqDTO.PasswordSendCode(EMAIL)))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    when(resetCodes.verifyResetTokenAndDelete("legacy-reset")).thenReturn(EMAIL);
    assertThatThrownBy(() -> auth.resetPassword(new AuthReqDTO.PasswordReset("legacy-reset", "NewPassword123!")))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    verifyNoInteractions(mail);
  }

  @Test
  void withdrawnSocialDoesNotBlockOtherProviderButCannotLoginAgain() {
    var kakaoUser = signupSocial(SocialProvider.KAKAO);
    em.find(User.class, kakaoUser.userId()).withdraw();
    assertThat(signupSocial(SocialProvider.NAVER).userId()).isNotEqualTo(kakaoUser.userId());
    assertThatThrownBy(() -> loginSocial(SocialProvider.KAKAO))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.USER_WITHDRAWN));
  }

  @Test
  void withdrawnEmailAccountStillBlocksEmailSignupButNotSocialSignup() {
    var emailUser = auth.signUp(emailSignup());
    em.find(User.class, emailUser.userId()).withdraw();
    assertThat(signupSocial(SocialProvider.KAKAO).userId()).isNotEqualTo(emailUser.userId());
    assertThatThrownBy(() -> auth.signUp(emailSignup()))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.USER_WITHDRAWN));
    assertThatThrownBy(() -> auth.checkEmail(EMAIL))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.USER_WITHDRAWN));
  }

  private AuthResDTO.Token signupSocial(SocialProvider provider) {
    when(temporaryTokens.getPayload(provider.name())).thenReturn(
      new SocialSignupTokenPayload(provider, "same-provider-id", EMAIL, "이름", null));
    return auth.completeSocialSignup(new AuthReqDTO.SocialSignupComplete(provider.name(), "회원",
      Gender.NONE, "2000", region.getId(), agreements()));
  }

  private AuthResDTO.SocialLogin loginSocial(SocialProvider provider) {
    var info = new SocialUserInfo(provider, "same-provider-id", EMAIL, "이름", null);
    when(kakaoToken.getAccessToken(anyString(), anyString())).thenReturn("access");
    when(kakao.getUserInfo("access")).thenReturn(info);
    when(naver.getUserInfo("access")).thenReturn(info);
    return auth.socialLogin(new AuthReqDTO.SocialLogin(provider, "access", "code", "https://example.com/callback"));
  }

  private AuthReqDTO.SignUp emailSignup() {
    return new AuthReqDTO.SignUp(EMAIL, "email-token", "Password123!", "이메일회원",
      Gender.NONE, "2000", region.getId(), agreements());
  }

  private List<TermsReqDTO.Agreement> agreements() {
    return List.of(new TermsReqDTO.Agreement(TermsType.SERVICE, "1.0", true),
      new TermsReqDTO.Agreement(TermsType.PRIVACY, "1.0", true));
  }
}
