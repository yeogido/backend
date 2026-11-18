package com.yeogido.backend.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.yeogido.backend.domain.auth.service.RefreshTokenService;
import com.yeogido.backend.domain.business.dto.request.BusinessPromotionRequest;
import com.yeogido.backend.domain.business.entity.BusinessPromotion;
import com.yeogido.backend.domain.business.enums.DayOfWeek;
import com.yeogido.backend.domain.business.enums.PromotionCategory;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import com.yeogido.backend.domain.business.repository.BusinessPromotionRepository;
import com.yeogido.backend.domain.business.service.BusinessPromotionService;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.place.dto.request.PlaceRequest;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.user.client.NtsBusinessVerificationClient;
import com.yeogido.backend.domain.user.dto.BusinessVerifyReqDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyResDTO;
import com.yeogido.backend.domain.user.dto.nts.NtsBusinessVerifyDTO;
import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.exception.BusinessVerificationErrorCode;
import com.yeogido.backend.domain.user.repository.BusinessInfoRepository;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사업자 인증 해제(회원 탈퇴, 폐업 사업자의 가게 넘겨주기)가 실제 트랜잭션에서
 * 사업자 정보, 홍보글, 권한을 함께 정리하고 실패 시 되돌리는지 확인합니다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:business-revocation;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class BusinessInfoRevocationIntegrationTest {

    /** 테스트들이 같은 인메모리 DB 를 쓰므로 사업자번호는 테스트마다 새로 뽑는다(10자리). */
    private static final AtomicLong BUSINESS_NUMBER_SEQUENCE = new AtomicLong(1_000_000_000L);

    @Autowired private UserService userService;
    @Autowired private BusinessVerificationService businessVerificationService;
    @Autowired private BusinessPromotionService businessPromotionService;
    @Autowired private BusinessInfoRepository businessInfoRepository;
    @Autowired private BusinessPromotionRepository businessPromotionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private RefreshTokenService refreshTokenService;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;
    @MockitoBean private NtsBusinessVerificationClient ntsClient;

    @BeforeEach
    void stubFileMove() {
        // S3 없이 동작하도록 이미지 이동은 key 를 그대로 돌려준다.
        when(fileService.moveToDirectory(anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void withdrawalRevokesBusinessInfoSoNumberAndPlaceCanBeReused() {
        Fixture fixture = fixture();

        userService.withdraw(fixture.previousOwner());

        BusinessInfo revoked = find(BusinessInfo.class, fixture.businessInfo());
        assertThat(revoked.getVerificationStatus()).isEqualTo(BusinessVerificationStatus.REVOKED);
        assertThat(revoked.getBusinessNumber()).isNull();
        assertThat(placeIdOf(revoked)).isNull();
        assertThat(statusOf(fixture.promotion())).isEqualTo(PromotionStatus.DELETED);
        assertThat(roleOf(fixture.previousOwner())).isEqualTo(UserRole.USER);

        // 새 계정이 같은 사업자번호와 같은 가게로 인증에 성공한다.
        stubApplicantVerified(fixture.previousNumber());
        BusinessVerifyResDTO response = businessVerificationService.verifyBusiness(
                fixture.newOwner(), verifyRequest(fixture, fixture.previousNumber()));

        BusinessInfo reissued = find(BusinessInfo.class, response.businessInfoId());
        assertThat(reissued.getId()).isNotEqualTo(fixture.businessInfo());
        assertThat(reissued.getBusinessNumber()).isEqualTo(fixture.previousNumber());
        assertThat(placeIdOf(reissued)).isEqualTo(fixture.place());
        assertThat(roleOf(fixture.newOwner())).isEqualTo(UserRole.BUSINESS);
    }

    @Test
    void closedPreviousOwnerHandsPlaceOverToNewOwner() {
        Fixture fixture = fixture();
        stubPreviousOwnerStatus(fixture, "03");
        stubApplicantVerified(fixture.newNumber());

        BusinessVerifyResDTO response = businessVerificationService.verifyBusiness(
                fixture.newOwner(), verifyRequest(fixture, fixture.newNumber()));

        BusinessInfo revoked = find(BusinessInfo.class, fixture.businessInfo());
        assertThat(revoked.getVerificationStatus()).isEqualTo(BusinessVerificationStatus.REVOKED);
        assertThat(revoked.getBusinessNumber()).isNull();
        assertThat(placeIdOf(revoked)).isNull();
        assertThat(statusOf(fixture.promotion())).isEqualTo(PromotionStatus.DELETED);
        assertThat(roleOf(fixture.previousOwner())).isEqualTo(UserRole.USER);

        BusinessInfo handedOver = find(BusinessInfo.class, response.businessInfoId());
        assertThat(handedOver.getVerificationStatus()).isEqualTo(BusinessVerificationStatus.APPROVED);
        assertThat(placeIdOf(handedOver)).isEqualTo(fixture.place());
        assertThat(roleOf(fixture.newOwner())).isEqualTo(UserRole.BUSINESS);
        assertThat(businessVerificationService.getApprovedBusinesses(fixture.previousOwner())).isEmpty();
    }

    @Test
    void suspendedPreviousOwnerKeepsPlace() {
        Fixture fixture = fixture();
        stubPreviousOwnerStatus(fixture, "02");

        assertThatThrownBy(() -> businessVerificationService.verifyBusiness(
                fixture.newOwner(), verifyRequest(fixture, fixture.newNumber())))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(BusinessVerificationErrorCode.BUSINESS_PLACE_ALREADY_LINKED);

        assertPreviousOwnerUntouched(fixture);
    }

    @Test
    void takeoverIsRolledBackWhenApplicantFailsVerification() {
        Fixture fixture = fixture();
        stubPreviousOwnerStatus(fixture, "03");
        // 신청자 본인은 진위확인 실패 + 계속사업자 → 4002
        when(ntsClient.verify(eq(fixture.newNumber()), any(), anyString()))
                .thenReturn(new NtsBusinessVerifyDTO.Result(fixture.newNumber(), "02", "확인할 수 없습니다.", null));
        when(ntsClient.getBusinessStatus(fixture.newNumber())).thenReturn(status(fixture.newNumber(), "01"));

        assertThatThrownBy(() -> businessVerificationService.verifyBusiness(
                fixture.newOwner(), verifyRequest(fixture, fixture.newNumber())))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(BusinessVerificationErrorCode.BUSINESS_VERIFICATION_FAILED);

        // 4번(해제)까지 갔더라도 5번(본인 확인) 실패로 전부 되돌아간다.
        assertPreviousOwnerUntouched(fixture);
    }

    @Test
    void newOwnerRegistersPromotionOnReusedRowWithoutPreviousContent() {
        Fixture fixture = fixture();
        stubPreviousOwnerStatus(fixture, "03");
        stubApplicantVerified(fixture.newNumber());
        Long newBusinessInfoId = businessVerificationService.verifyBusiness(
                fixture.newOwner(), verifyRequest(fixture, fixture.newNumber())).businessInfoId();

        Long promotionId = businessPromotionService.registerBusinessPromotion(
                fixture.newOwner(),
                promotionRequest(newBusinessInfoId, "새 주인의 소개글", "TUESDAY", "promotions/new.jpg")
        ).promotionId();

        // 이전 사장의 홍보글 행을 재사용하되(ID 유지) 작성자와 내용은 모두 새 것이다.
        assertThat(promotionId).isEqualTo(fixture.promotion());
        BusinessPromotion reused = find(BusinessPromotion.class, promotionId);
        assertThat(reused.getStatus()).isEqualTo(PromotionStatus.ACTIVE);
        assertThat(userIdOf(reused)).isEqualTo(fixture.newOwner());
        assertThat(reused.getShortDescription()).isEqualTo("새 주인의 소개글");
        assertThat(operatingDaysOf(promotionId)).containsExactly("TUESDAY");
        assertThat(imageKeysOf(promotionId)).containsExactly("promotions/new.jpg");
    }

    @Test
    void sameOwnerReRegistrationStillReusesRow() {
        Fixture fixture = fixture();
        businessPromotionService.deleteBusinessPromotion(fixture.previousOwner(), fixture.promotion());

        Long promotionId = businessPromotionService.registerBusinessPromotion(
                fixture.previousOwner(),
                promotionRequest(fixture.businessInfo(), "다시 올린 소개글", "WEDNESDAY", "promotions/again.jpg")
        ).promotionId();

        assertThat(promotionId).isEqualTo(fixture.promotion());
        assertThat(statusOf(promotionId)).isEqualTo(PromotionStatus.ACTIVE);
        assertThat(operatingDaysOf(promotionId)).containsExactly("WEDNESDAY");
    }

    private void assertPreviousOwnerUntouched(Fixture fixture) {
        BusinessInfo kept = find(BusinessInfo.class, fixture.businessInfo());
        assertThat(kept.getVerificationStatus()).isEqualTo(BusinessVerificationStatus.APPROVED);
        assertThat(kept.getBusinessNumber()).isEqualTo(fixture.previousNumber());
        assertThat(placeIdOf(kept)).isEqualTo(fixture.place());
        assertThat(statusOf(fixture.promotion())).isEqualTo(PromotionStatus.ACTIVE);
        assertThat(roleOf(fixture.previousOwner())).isEqualTo(UserRole.BUSINESS);
        assertThat(roleOf(fixture.newOwner())).isEqualTo(UserRole.USER);
        assertThat(businessInfoRepository.findByBusinessNumber(fixture.newNumber())).isEmpty();
    }

    // ───────── 국세청 Mock ─────────

    private void stubPreviousOwnerStatus(Fixture fixture, String statusCode) {
        when(ntsClient.getBusinessStatus(fixture.previousNumber()))
                .thenReturn(status(fixture.previousNumber(), statusCode));
    }

    private void stubApplicantVerified(String businessNumber) {
        when(ntsClient.verify(eq(businessNumber), any(), anyString()))
                .thenReturn(new NtsBusinessVerifyDTO.Result(
                        businessNumber, "01", "확인되었습니다.", status(businessNumber, "01")));
    }

    private static NtsBusinessVerifyDTO.Status status(String businessNumber, String statusCode) {
        return new NtsBusinessVerifyDTO.Status(
                businessNumber, "", statusCode, "부가가치세 일반과세자", "01", "");
    }

    // ───────── 요청 ─────────

    private static BusinessVerifyReqDTO verifyRequest(Fixture fixture, String businessNumber) {
        PlaceRequest placeRequest = new PlaceRequest(
                fixture.placeExternalId(), "KAKAO", "넘겨받는 카페", "CE7",
                "부산 수영구 광안해변로 219", "부산 수영구 광안동 192-1",
                BigDecimal.valueOf(35.1532), BigDecimal.valueOf(129.1186), 1L);
        return new BusinessVerifyReqDTO(
                businessNumber, LocalDate.of(2024, 1, 15), "홍길동",
                "businesses/certificate.jpg", "넘겨받는 카페", "부산 수영구 광안해변로 219", placeRequest);
    }

    private static BusinessPromotionRequest.Register promotionRequest(
            Long businessInfoId, String shortDescription, String dayOfWeek, String imageKey) {
        return new BusinessPromotionRequest.Register(
                businessInfoId,
                shortDescription,
                List.of(new BusinessPromotionRequest.BusinessHour(
                        dayOfWeek, LocalTime.of(10, 0), LocalTime.of(20, 0))),
                null,
                "051-123-4567",
                null,
                PromotionCategory.CAFE,
                List.of(new BusinessPromotionRequest.Image(imageKey, 1))
        );
    }

    // ───────── 픽스처: 기존 사장(사업장 + 활성 홍보글)과 새 주인 ─────────

    private Fixture fixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Region region = Region.builder().name("부산").type(RegionType.REGION).build();
            entityManager.persist(region);

            String previousNumber = String.valueOf(BUSINESS_NUMBER_SEQUENCE.incrementAndGet());
            String newNumber = String.valueOf(BUSINESS_NUMBER_SEQUENCE.incrementAndGet());
            User previousOwner = user("기존 사장", region, UserRole.BUSINESS);
            User newOwner = user("새 주인", region, UserRole.USER);

            // 테스트들이 같은 인메모리 DB 를 쓰므로 장소 외부 ID 는 테스트마다 다르게 둔다.
            String placeExternalId = "takeover-place-" + UUID.randomUUID();
            Place place = Place.builder().region(region).externalPlaceId(placeExternalId)
                    .source(PlaceSource.KAKAO).name("넘겨받는 카페").categoryGroupCode("CE7")
                    .latitude(BigDecimal.valueOf(35.1532)).longitude(BigDecimal.valueOf(129.1186)).build();
            entityManager.persist(place);

            BusinessInfo businessInfo = BusinessInfo.builder().user(previousOwner).place(place)
                    .businessNumber(previousNumber).openingDate(LocalDate.of(2020, 1, 1))
                    .representativeName("기존 사장").registrationImageKey("businesses/old.jpg")
                    .businessName("넘겨받는 카페").businessAddress("부산 수영구 광안해변로 219")
                    .verificationStatus(BusinessVerificationStatus.APPROVED)
                    .verifiedAt(LocalDateTime.now()).build();
            entityManager.persist(businessInfo);
            entityManager.flush();

            Long promotionId = businessPromotionService.registerBusinessPromotion(
                    previousOwner.getId(),
                    promotionRequest(businessInfo.getId(), "기존 사장의 소개글", "MONDAY", "promotions/old.jpg")
            ).promotionId();

            return new Fixture(previousOwner.getId(), newOwner.getId(), place.getId(), placeExternalId,
                    previousNumber, newNumber, businessInfo.getId(), promotionId);
        });
    }

    private User user(String name, Region region, UserRole role) {
        User user = User.builder().nickname(name).email(UUID.randomUUID() + "@test.com").gender(Gender.FEMALE)
                .birthYear("2000").region(region).role(role).status(UserStatus.ACTIVE).build();
        entityManager.persist(user);
        return user;
    }

    // ───────── 조회 (새 트랜잭션에서 DB 상태를 다시 읽는다) ─────────

    private <T> T find(Class<T> type, Long id) {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager.find(type, id));
    }

    private Long placeIdOf(BusinessInfo businessInfo) {
        List<Long> placeIds = new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery("select b.place.id from BusinessInfo b where b.id = :id", Long.class)
                        .setParameter("id", businessInfo.getId()).getResultList());
        return placeIds.isEmpty() ? null : placeIds.get(0);
    }

    private Long userIdOf(BusinessPromotion promotion) {
        return new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery("select p.user.id from BusinessPromotion p where p.id = :id", Long.class)
                        .setParameter("id", promotion.getId()).getSingleResult());
    }

    private PromotionStatus statusOf(Long promotionId) {
        return businessPromotionRepository.findById(promotionId).orElseThrow().getStatus();
    }

    private UserRole roleOf(Long userId) {
        return userRepository.findById(userId).orElseThrow().getRole();
    }

    private List<String> operatingDaysOf(Long promotionId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery(
                        "select d.dayOfWeek from BusinessOperatingDay d where d.promotion.id = :id",
                        DayOfWeek.class).setParameter("id", promotionId).getResultList()
                        .stream().map(DayOfWeek::name).toList());
    }

    private List<String> imageKeysOf(Long promotionId) {
        return new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery(
                        "select i.imageKey from BusinessPromotionImage i where i.promotion.id = :id",
                        String.class).setParameter("id", promotionId).getResultList());
    }

    private record Fixture(
            Long previousOwner, Long newOwner, Long place, String placeExternalId,
            String previousNumber, String newNumber, Long businessInfo, Long promotion
    ) {
    }
}
