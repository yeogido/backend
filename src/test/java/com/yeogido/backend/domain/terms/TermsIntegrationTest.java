package com.yeogido.backend.domain.terms;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.auth.client.*;
import com.yeogido.backend.domain.auth.dto.*;
import com.yeogido.backend.domain.auth.entity.SocialAccount;
import com.yeogido.backend.domain.auth.enums.SocialProvider;
import com.yeogido.backend.domain.auth.service.*;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.service.FriendCodeGenerator;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.*;
import com.yeogido.backend.global.config.JpaConfig;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.LocalDateTime;
import java.util.Arrays;
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
@Import({JpaConfig.class, TermsService.class, AuthServiceImpl.class, JwtTokenProvider.class, ObjectMapper.class})
@TestPropertySource(properties = {
  "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
  "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
  "app.jwt.secret=test-only-signing-key"
})
class TermsIntegrationTest {
  @Autowired TermsService terms;
  @Autowired AuthServiceImpl auth;
  @Autowired TestEntityManager em;
  @Autowired UserTermsAgreementRepository agreements;
  @Autowired TermsDocumentRepository documents;

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
    for (var type : TermsType.values()) document(type, "1.0", LocalDateTime.now().minusDays(1));
  }

  @Test
  void publishedDocumentsOnlyAndMissingCatalogFailsClosed() {
    document(TermsType.SERVICE, "2.0", LocalDateTime.now().plusDays(1));
    assertThat(terms.currentDocuments()).extracting(TermsDTO.Document::version).containsOnly("1.0");
    documents.deleteAll();
    assertThatThrownBy(terms::currentDocuments).isInstanceOfSatisfying(GeneralException.class,
      e -> assertThat(e.getErrorCode()).isEqualTo(TermsErrorCode.NOT_CONFIGURED));
    assertThat(terms.isAgreementRequired(999L)).isTrue();
  }

  @Test
  void rejectsMissingFalseDuplicateAndStaleAgreements() {
    var valid = request();
    for (var invalid : Arrays.asList(null, List.<TermsDTO.Agreement>of(), List.of(valid.get(0)),
        List.of(valid.get(0), valid.get(0)),
        List.of(valid.get(0), new TermsDTO.Agreement(TermsType.PRIVACY, "1.0", false)),
        Arrays.asList(valid.get(0), null),
        List.of(valid.get(0), new TermsDTO.Agreement(TermsType.PRIVACY, "1.0", null)))) {
      assertThatThrownBy(() -> terms.validate(invalid)).isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(TermsErrorCode.REQUIRED_AGREEMENT));
    }
    assertThatThrownBy(() -> terms.validate(List.of(valid.get(0),
      new TermsDTO.Agreement(TermsType.PRIVACY, "old", true))))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(TermsErrorCode.VERSION_MISMATCH));
  }

  @Test
  void existingMemberConsentIsIdempotentAndNewVersionPreservesHistory() {
    var user = user();
    assertThat(terms.status(user.getId()).termsAgreementRequired()).isTrue();
    var first = terms.agree(user.getId(), request());
    assertThat(first.termsAgreementRequired()).isFalse();
    assertThat(terms.agree(user.getId(), request())).isEqualTo(first);
    assertThat(agreements.count()).isEqualTo(2);

    document(TermsType.SERVICE, "2.0", LocalDateTime.now().minusMinutes(1));
    assertThat(terms.status(user.getId()).termsAgreementRequired()).isTrue();
    assertThat(terms.agree(user.getId(), List.of(new TermsDTO.Agreement(TermsType.SERVICE, "2.0", true),
      new TermsDTO.Agreement(TermsType.PRIVACY, "1.0", true))).termsAgreementRequired()).isFalse();
    assertThat(agreements.count()).isEqualTo(3);
  }

  @Test
  void invalidSignupHasNoExternalSideEffects() {
    assertThatThrownBy(() -> auth.signUp(signup(null))).isInstanceOf(GeneralException.class);
    assertThatThrownBy(() -> auth.completeSocialSignup(socialSignup(null))).isInstanceOf(GeneralException.class);
    verifyNoInteractions(emailVerification, temporaryTokens, images, refreshTokens);
    assertThat(agreements.count()).isZero();
  }

  @Test
  void missingOrStaleDocumentsPreventSignupBeforeEmailTokenConsumption() {
    document(TermsType.SERVICE, "2.0", LocalDateTime.now().minusMinutes(1));
    assertThatThrownBy(() -> auth.signUp(signup(request())))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(TermsErrorCode.VERSION_MISMATCH));
    assertThatThrownBy(() -> auth.completeSocialSignup(socialSignup(request())))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(TermsErrorCode.VERSION_MISMATCH));
    documents.deleteAll();
    assertThatThrownBy(() -> auth.signUp(signup(request())))
      .isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(TermsErrorCode.NOT_CONFIGURED));
    verifyNoInteractions(emailVerification, temporaryTokens, images, refreshTokens);
    assertThat(agreements.count()).isZero();
  }

  @Test
  void withdrawnMemberCannotAddConsent() {
    var user = user();
    user.withdraw();
    assertThatThrownBy(() -> terms.agree(user.getId(), request())).isInstanceOf(GeneralException.class);
    assertThat(agreements.count()).isZero();
  }

  @Test
  void emailSignupStoresBothConsentsAndLoginDoesNotAskAgain() {
    when(passwords.encode(anyString())).thenReturn("encoded");
    when(passwords.matches(anyString(), eq("encoded"))).thenReturn(true);
    when(friendCodes.generateUnique()).thenReturn("ABCD1234");
    var signup = auth.signUp(signup(request()));
    em.flush();
    em.clear();
    assertThat(terms.status(signup.userId()).termsAgreementRequired()).isFalse();
    assertThat(agreements.count()).isEqualTo(2);
    var login = auth.login(new AuthReqDTO.Login("new@example.com", "password"));
    assertThat(login.termsAgreementRequired()).isFalse();
    verify(emailVerification).deleteToken("email-token");
  }

  @ParameterizedTest
  @EnumSource(value = SocialProvider.class, names = {"KAKAO", "NAVER"})
  void socialSignupStoresBothConsents(SocialProvider provider) {
    when(temporaryTokens.getPayload("temp-token")).thenReturn(
      new SocialSignupTokenPayload(provider, "provider-id", "social@example.com", "이름", null));
    when(friendCodes.generateUnique()).thenReturn("ABCD1234");
    var result = auth.completeSocialSignup(socialSignup(request()));
    em.flush();
    em.clear();
    assertThat(result.termsAgreementRequired()).isFalse();
    assertThat(terms.status(result.userId()).agreements()).hasSize(2);
    assertThat(agreements.count()).isEqualTo(2);
    verify(temporaryTokens).deleteToken("temp-token");
  }

  @Test
  void existingEmailLoginAndReissueReflectCurrentConsent() {
    var user = user();
    when(passwords.matches(anyString(), eq("encoded"))).thenReturn(true);
    var token = auth.login(new AuthReqDTO.Login(user.getEmail(), "password"));
    assertThat(token.termsAgreementRequired()).isTrue();
    terms.agree(user.getId(), request());
    when(refreshTokens.rotate(eq(user.getId()), anyString(), anyString(), anyString())).thenReturn(true);
    var refreshed = auth.reissue(new AuthReqDTO.Reissue(token.refreshToken()));
    assertThat(refreshed.termsAgreementRequired()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = SocialProvider.class, names = {"KAKAO", "NAVER"})
  void existingSocialLoginReflectsConsentWithoutRequiringSignup(SocialProvider provider) {
    var user = user();
    em.persist(SocialAccount.builder().user(user).provider(provider).providerId("social-id").build());
    var info = new SocialUserInfo(provider, "social-id", user.getEmail(), "이름", null);
    var req = new AuthReqDTO.SocialLogin(provider, "access", "code", "https://example.com/callback");
    if (provider == SocialProvider.KAKAO) {
      when(kakaoToken.getAccessToken(anyString(), anyString())).thenReturn("access");
      when(kakao.getUserInfo("access")).thenReturn(info);
    } else {
      when(naver.getUserInfo("access")).thenReturn(info);
    }
    assertThat(auth.socialLogin(req).termsAgreementRequired()).isTrue();
    assertThat(auth.socialLogin(req).isNewUser()).isFalse();
    terms.agree(user.getId(), request());
    assertThat(auth.socialLogin(req).termsAgreementRequired()).isFalse();
    verifyNoInteractions(temporaryTokens);
  }

  private TermsDocument document(TermsType type, String version, LocalDateTime effectiveAt) {
    return em.persist(TermsDocument.builder().type(type).version(version).title("테스트 약관")
      .content("테스트 전용 약관 전문").effectiveAt(effectiveAt).build());
  }

  private User user() {
    return em.persist(User.builder().email("existing@example.com").nickname("기존회원").password("encoded")
      .gender(Gender.NONE).birthYear("2000").region(region).role(UserRole.USER)
      .status(UserStatus.ACTIVE).friendCode("EXIST123").build());
  }

  private List<TermsDTO.Agreement> request() {
    return List.of(new TermsDTO.Agreement(TermsType.SERVICE, "1.0", true),
      new TermsDTO.Agreement(TermsType.PRIVACY, "1.0", true));
  }

  private AuthReqDTO.SignUp signup(List<TermsDTO.Agreement> agreements) {
    return new AuthReqDTO.SignUp("new@example.com", "email-token", "Password123!", "닉네임",
      Gender.NONE, "2000", region.getId(), agreements);
  }

  private AuthReqDTO.SocialSignupComplete socialSignup(List<TermsDTO.Agreement> agreements) {
    return new AuthReqDTO.SocialSignupComplete("temp-token", "소셜회원", Gender.NONE, "2000", region.getId(), agreements);
  }
}
