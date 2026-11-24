package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.business.entity.BusinessPromotion;
import com.yeogido.backend.domain.business.enums.PromotionCategory;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import com.yeogido.backend.domain.business.repository.BusinessPromotionRepository;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.place.dto.request.PlaceRequest;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.client.NtsBusinessVerificationClient;
import com.yeogido.backend.domain.user.dto.BusinessVerifyReqDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyResDTO;
import com.yeogido.backend.domain.user.dto.nts.NtsBusinessVerifyDTO;
import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.exception.BusinessVerificationErrorCode;
import com.yeogido.backend.domain.user.repository.BusinessInfoRepository;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessVerificationServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long BUSINESS_INFO_ID = 10L;

    private static final String BUSINESS_NUMBER = "1234567890";

    private static final LocalDate OPENING_DATE =
            LocalDate.of(2024, 1, 15);

    private static final String REPRESENTATIVE_NAME = "홍길동";

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessInfoRepository businessInfoRepository;

    @Mock
    private NtsBusinessVerificationClient ntsBusinessVerificationClient;

    @Mock
    private FileService fileService;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private BusinessPromotionRepository businessPromotionRepository;

    @InjectMocks
    private BusinessVerificationServiceImpl businessVerificationService;

    @Test
    void verifyBusiness_WhenNtsReturnsValidAndActive_PromotesUserAndSavesBusinessInfo() {

        // given
        User user = User.builder()
                .id(USER_ID)
                .role(UserRole.USER)
                .build();

        PlaceRequest placeRequest = new PlaceRequest(
                "123456789",
                "KAKAO",
                "여기도 카페",
                "CE7",
                "서울특별시 강남구 테헤란로 123",
                "서울특별시 강남구 역삼동 123",
                BigDecimal.valueOf(37.4979),
                BigDecimal.valueOf(127.0276),
                1L
        );

        BusinessVerifyReqDTO request = new BusinessVerifyReqDTO(
                "123-45-67890",
                OPENING_DATE,
                REPRESENTATIVE_NAME,
                "business-verifications/test/certificate.jpg",
                "여기도 카페",
                "서울특별시 강남구 테헤란로 123",
                placeRequest
        );

        Place place = Place.builder()
                .externalPlaceId("123456789")
                .source(PlaceSource.KAKAO)
                .name("여기도 카페")
                .categoryGroupCode("CE7")
                .roadAddress("서울특별시 강남구 테헤란로 123")
                .lotAddress("서울특별시 강남구 역삼동 123")
                .latitude(BigDecimal.valueOf(37.4979))
                .longitude(BigDecimal.valueOf(127.0276))
                .build();

        NtsBusinessVerifyDTO.Status status =
                new NtsBusinessVerifyDTO.Status(
                        BUSINESS_NUMBER,
                        "계속사업자",
                        "01",
                        "부가가치세 일반과세자",
                        "01",
                        ""
                );

        NtsBusinessVerifyDTO.Result ntsResult =
                new NtsBusinessVerifyDTO.Result(
                        BUSINESS_NUMBER,
                        "01",
                        "확인되었습니다.",
                        status
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(businessInfoRepository.findByBusinessNumber(
                BUSINESS_NUMBER
        )).thenReturn(Optional.empty());

        /*
         * 실제 국세청 API를 호출하지 않고,
         * 국세청에서 인증 성공 응답을 보냈다고 가정한다.
         */
        when(ntsBusinessVerificationClient.verify(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME
        )).thenReturn(ntsResult);

        when(fileService.moveToDirectory(
                "business-verifications/test/certificate.jpg",
                ImageDirectory.BUSINESS
        )).thenReturn("businesses/certificate.jpg");

        /*
         * 카카오 장소가 Place 테이블에 이미 저장되어 있다고 가정한다.
         */
        when(placeRepository.findBySourceAndExternalPlaceId(
                PlaceSource.KAKAO,
                "123456789"
        )).thenReturn(Optional.of(place));

        /*
         * 실제 DB에 저장하지 않고,
         * DB가 BusinessInfo ID를 생성했다고 가정한다.
         */
        when(businessInfoRepository.saveAndFlush(
                any(BusinessInfo.class)
        )).thenAnswer(invocation -> {

            BusinessInfo businessInfo =
                    invocation.getArgument(0, BusinessInfo.class);

            ReflectionTestUtils.setField(
                    businessInfo,
                    "id",
                    BUSINESS_INFO_ID
            );

            return businessInfo;
        });

        // when
        BusinessVerifyResDTO response =
                businessVerificationService.verifyBusiness(
                        USER_ID,
                        request
                );

        // then
        assertThat(response.role())
                .isEqualTo(UserRole.BUSINESS);

        assertThat(response.businessInfoId())
                .isEqualTo(BUSINESS_INFO_ID);

        assertThat(user.getRole())
                .isEqualTo(UserRole.BUSINESS);

        ArgumentCaptor<BusinessInfo> businessInfoCaptor =
                ArgumentCaptor.forClass(BusinessInfo.class);

        verify(businessInfoRepository)
                .saveAndFlush(businessInfoCaptor.capture());

        BusinessInfo savedBusinessInfo =
                businessInfoCaptor.getValue();

        assertThat(savedBusinessInfo.getUser())
                .isSameAs(user);

        assertThat(savedBusinessInfo.getPlace())
                .isSameAs(place);

        assertThat(savedBusinessInfo.getBusinessNumber())
                .isEqualTo(BUSINESS_NUMBER);

        assertThat(savedBusinessInfo.getOpeningDate())
                .isEqualTo(OPENING_DATE);

        assertThat(savedBusinessInfo.getRepresentativeName())
                .isEqualTo(REPRESENTATIVE_NAME);

        assertThat(savedBusinessInfo.getRegistrationImageKey())
                .isEqualTo("businesses/certificate.jpg");

        assertThat(savedBusinessInfo.getVerificationStatus())
                .isEqualTo(BusinessVerificationStatus.APPROVED);

        assertThat(savedBusinessInfo.getVerifiedAt())
                .isNotNull();

        verify(ntsBusinessVerificationClient).verify(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME
        );

        verify(placeRepository).findBySourceAndExternalPlaceId(
                PlaceSource.KAKAO,
                "123456789"
        );

        verify(ntsBusinessVerificationClient, never())
                .getBusinessStatus(anyString());
    }

    @Test
    void verifyBusiness_WhenPlaceSourceIsNotKakao_ThrowsInvalidRequest() {

        // given
        User user = User.builder()
                .id(USER_ID)
                .role(UserRole.USER)
                .build();

        PlaceRequest placeRequest = new PlaceRequest(
                "123456789",
                "TOUR_API",
                "여기도 카페",
                "CE7",
                "서울특별시 강남구 테헤란로 123",
                "서울특별시 강남구 역삼동 123",
                BigDecimal.valueOf(37.4979),
                BigDecimal.valueOf(127.0276),
                1L
        );

        BusinessVerifyReqDTO request = new BusinessVerifyReqDTO(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME,
                "business-verifications/test/certificate.jpg",
                "여기도 카페",
                "서울특별시 강남구 테헤란로 123",
                placeRequest
        );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(businessInfoRepository.findByBusinessNumber(
                BUSINESS_NUMBER
        )).thenReturn(Optional.empty());

        // when
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessVerificationService.verifyBusiness(
                        USER_ID,
                        request
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(GeneralErrorCode.INVALID_REQUEST);

        verifyNoInteractions(
                ntsBusinessVerificationClient,
                fileService,
                placeRepository
        );
    }

    @Test
    void verifyBusiness_WhenBusinessNumberDoesNotExist_ThrowsBusinessNotRegistered() {

        // given
        User user = User.builder()
                .id(USER_ID)
                .role(UserRole.USER)
                .build();

        PlaceRequest placeRequest = new PlaceRequest(
                "invalid-place",
                "KAKAO",
                "테스트 사업장",
                "CE7",
                "서울특별시 강남구 테헤란로 123",
                "서울특별시 강남구 역삼동 123",
                BigDecimal.valueOf(37.4979),
                BigDecimal.valueOf(127.0276),
                1L
        );

        BusinessVerifyReqDTO request = new BusinessVerifyReqDTO(
                "0000000000",
                OPENING_DATE,
                REPRESENTATIVE_NAME,
                "temp/test.jpg",
                "테스트 사업장",
                "서울특별시 강남구 테헤란로 123",
                placeRequest
        );

        NtsBusinessVerifyDTO.Result verificationResult =
                new NtsBusinessVerifyDTO.Result(
                        "0000000000",
                        "02",
                        "확인할 수 없습니다.",
                        null
                );

        NtsBusinessVerifyDTO.Status status =
                new NtsBusinessVerifyDTO.Status(
                        "0000000000",
                        "",
                        "",
                        "국세청에 등록되지 않은 사업자등록번호입니다.",
                        "",
                        ""
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(businessInfoRepository.findByBusinessNumber(
                "0000000000"
        )).thenReturn(Optional.empty());

        when(ntsBusinessVerificationClient.verify(
                "0000000000",
                OPENING_DATE,
                REPRESENTATIVE_NAME
        )).thenReturn(verificationResult);

        when(ntsBusinessVerificationClient.getBusinessStatus(
                "0000000000"
        )).thenReturn(status);

        // when
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessVerificationService.verifyBusiness(
                        USER_ID,
                        request
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        BusinessVerificationErrorCode.BUSINESS_NOT_REGISTERED
                );

        verify(ntsBusinessVerificationClient).verify(
                "0000000000",
                OPENING_DATE,
                REPRESENTATIVE_NAME
        );

        verify(ntsBusinessVerificationClient).getBusinessStatus(
                "0000000000"
        );

        verifyNoInteractions(fileService);
    }

    @Test
    void verifyBusiness_WhenBusinessExistsButVerificationInfoDoesNotMatch_ThrowsVerificationFailed() {

        // given
        User user = User.builder()
                .id(USER_ID)
                .role(UserRole.USER)
                .build();

        PlaceRequest placeRequest = new PlaceRequest(
                "123456789",
                "KAKAO",
                "테스트 사업장",
                "CE7",
                "서울특별시 강남구 테헤란로 123",
                "서울특별시 강남구 역삼동 123",
                BigDecimal.valueOf(37.4979),
                BigDecimal.valueOf(127.0276),
                1L
        );

        BusinessVerifyReqDTO request = new BusinessVerifyReqDTO(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME,
                "temp/test.jpg",
                "테스트 사업장",
                "서울특별시 강남구 테헤란로 123",
                placeRequest
        );

        NtsBusinessVerifyDTO.Result verificationResult =
                new NtsBusinessVerifyDTO.Result(
                        BUSINESS_NUMBER,
                        "02",
                        "확인할 수 없습니다.",
                        null
                );

        NtsBusinessVerifyDTO.Status status =
                new NtsBusinessVerifyDTO.Status(
                        BUSINESS_NUMBER,
                        "계속사업자",
                        "01",
                        "부가가치세 일반과세자",
                        "01",
                        ""
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(businessInfoRepository.findByBusinessNumber(
                BUSINESS_NUMBER
        )).thenReturn(Optional.empty());

        when(ntsBusinessVerificationClient.verify(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME
        )).thenReturn(verificationResult);

        when(ntsBusinessVerificationClient.getBusinessStatus(
                BUSINESS_NUMBER
        )).thenReturn(status);

        // when
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessVerificationService.verifyBusiness(
                        USER_ID,
                        request
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(
                        BusinessVerificationErrorCode.BUSINESS_VERIFICATION_FAILED
                );

        verify(ntsBusinessVerificationClient).verify(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME
        );

        verify(ntsBusinessVerificationClient).getBusinessStatus(
                BUSINESS_NUMBER
        );

        verifyNoInteractions(fileService);
    }

    @Test
    void verifyBusiness_WhenAuthenticAndStatusIsSuspended_ThrowsBusinessSuspended() {
        assertRejectedByStatusAfterValidAuthenticity(
                "02",
                BusinessVerificationErrorCode.BUSINESS_SUSPENDED
        );
    }

    @Test
    void verifyBusiness_WhenAuthenticAndStatusIsClosed_ThrowsBusinessClosed() {
        assertRejectedByStatusAfterValidAuthenticity(
                "03",
                BusinessVerificationErrorCode.BUSINESS_CLOSED
        );
    }

    @Test
    void verifyBusiness_WhenNotAuthenticAndStatusIsSuspended_ThrowsBusinessSuspended() {
        assertRejectedByStatusAfterFailedAuthenticity(
                "02",
                BusinessVerificationErrorCode.BUSINESS_SUSPENDED
        );
    }

    @Test
    void verifyBusiness_WhenNotAuthenticAndStatusIsClosed_ThrowsBusinessClosed() {
        assertRejectedByStatusAfterFailedAuthenticity(
                "03",
                BusinessVerificationErrorCode.BUSINESS_CLOSED
        );
    }

    /**
     * 진위확인은 성공(valid "01")했지만 영업 상태 코드가 계속사업자가 아닌 경우.
     * 상태 조회 API를 따로 호출하지 않고, 이미지 이동과 저장이 일어나지 않아야 한다.
     */
    private void assertRejectedByStatusAfterValidAuthenticity(
            String statusCode,
            BusinessVerificationErrorCode expectedErrorCode
    ) {
        // given
        User user = User.builder()
                .id(USER_ID)
                .role(UserRole.USER)
                .build();

        BusinessVerifyReqDTO request = verifyRequest();

        NtsBusinessVerifyDTO.Result verificationResult =
                new NtsBusinessVerifyDTO.Result(
                        BUSINESS_NUMBER,
                        "01",
                        "확인되었습니다.",
                        ntsStatus(statusCode)
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(businessInfoRepository.findByBusinessNumber(BUSINESS_NUMBER))
                .thenReturn(Optional.empty());

        when(ntsBusinessVerificationClient.verify(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME
        )).thenReturn(verificationResult);

        // when
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessVerificationService.verifyBusiness(
                        USER_ID,
                        request
                )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode);

        verify(ntsBusinessVerificationClient, never())
                .getBusinessStatus(anyString());
        verify(businessInfoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(fileService);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    /**
     * 진위확인이 실패(valid "02")해서 상태 조회를 한 경우.
     * 계속사업자가 아니면 정보 불일치(4002)보다 영업 상태 안내가 우선한다.
     */
    private void assertRejectedByStatusAfterFailedAuthenticity(
            String statusCode,
            BusinessVerificationErrorCode expectedErrorCode
    ) {
        // given
        User user = User.builder()
                .id(USER_ID)
                .role(UserRole.USER)
                .build();

        BusinessVerifyReqDTO request = verifyRequest();

        NtsBusinessVerifyDTO.Result verificationResult =
                new NtsBusinessVerifyDTO.Result(
                        BUSINESS_NUMBER,
                        "02",
                        "확인할 수 없습니다.",
                        null
                );

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(businessInfoRepository.findByBusinessNumber(BUSINESS_NUMBER))
                .thenReturn(Optional.empty());

        when(ntsBusinessVerificationClient.verify(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME
        )).thenReturn(verificationResult);

        when(ntsBusinessVerificationClient.getBusinessStatus(BUSINESS_NUMBER))
                .thenReturn(ntsStatus(statusCode));

        // when
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessVerificationService.verifyBusiness(
                        USER_ID,
                        request
                )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode);

        verify(businessInfoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(fileService);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    private static BusinessVerifyReqDTO verifyRequest() {
        PlaceRequest placeRequest = new PlaceRequest(
                "123456789",
                "KAKAO",
                "테스트 사업장",
                "CE7",
                "서울특별시 강남구 테헤란로 123",
                "서울특별시 강남구 역삼동 123",
                BigDecimal.valueOf(37.4979),
                BigDecimal.valueOf(127.0276),
                1L
        );

        return new BusinessVerifyReqDTO(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME,
                "temp/test.jpg",
                "테스트 사업장",
                "서울특별시 강남구 테헤란로 123",
                placeRequest
        );
    }

    private static NtsBusinessVerifyDTO.Status ntsStatus(String statusCode) {
        return new NtsBusinessVerifyDTO.Status(
                BUSINESS_NUMBER,
                switch (statusCode) {
                    case "01" -> "계속사업자";
                    case "02" -> "휴업자";
                    case "03" -> "폐업자";
                    default -> "";
                },
                statusCode,
                "부가가치세 일반과세자",
                "01",
                ""
        );
    }

    // ───────── 장소가 이미 다른 사업자에 연결된 경우 (작업 2, 3) ─────────

    private static final Long PREVIOUS_OWNER_ID = 2L;
    private static final Long LINKED_PLACE_ID = 100L;
    private static final String PREVIOUS_BUSINESS_NUMBER = "9999999999";

    /** 신청자, 기존 사장, 둘 사이에 놓인 장소와 사업자 정보, 기존 사장의 활성 홍보글 */
    private record LinkedPlaceFixture(
            User applicant,
            User previousOwner,
            Place place,
            BusinessInfo linkedBusinessInfo,
            BusinessPromotion promotion
    ) {
    }

    /**
     * 신청자가 요청한 장소에 기존 사장의 승인된 사업자 정보가 이미 연결되어 있는 상황을 만든다.
     * 기존 사장 번호의 국세청 상태 조회까지는 모든 케이스에서 일어나므로 여기서 함께 stub 한다.
     */
    private LinkedPlaceFixture linkedPlaceFixture() {
        User applicant = User.builder()
                .id(USER_ID)
                .role(UserRole.USER)
                .build();

        User previousOwner = User.builder()
                .id(PREVIOUS_OWNER_ID)
                .role(UserRole.BUSINESS)
                .build();

        Place place = Place.builder()
                .externalPlaceId("123456789")
                .source(PlaceSource.KAKAO)
                .name("테스트 사업장")
                .build();
        ReflectionTestUtils.setField(place, "id", LINKED_PLACE_ID);

        BusinessInfo linkedBusinessInfo = BusinessInfo.builder()
                .id(20L)
                .user(previousOwner)
                .place(place)
                .businessNumber(PREVIOUS_BUSINESS_NUMBER)
                .verificationStatus(BusinessVerificationStatus.APPROVED)
                .verifiedAt(LocalDateTime.now())
                .build();

        BusinessPromotion promotion = BusinessPromotion.builder()
                .id(30L)
                .place(place)
                .user(previousOwner)
                .promotionCategory(PromotionCategory.CAFE)
                .status(PromotionStatus.ACTIVE)
                .build();

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(applicant));

        when(businessInfoRepository.findByBusinessNumber(BUSINESS_NUMBER))
                .thenReturn(Optional.empty());

        when(placeRepository.findBySourceAndExternalPlaceId(
                PlaceSource.KAKAO,
                "123456789"
        )).thenReturn(Optional.of(place));

        when(businessInfoRepository.findByPlace_Id(LINKED_PLACE_ID))
                .thenReturn(Optional.of(linkedBusinessInfo));

        return new LinkedPlaceFixture(
                applicant,
                previousOwner,
                place,
                linkedBusinessInfo,
                promotion
        );
    }

    private static NtsBusinessVerifyDTO.Status previousOwnerStatus(String statusCode) {
        return new NtsBusinessVerifyDTO.Status(
                PREVIOUS_BUSINESS_NUMBER,
                "",
                statusCode,
                "부가가치세 일반과세자",
                "01",
                ""
        );
    }

    /** 기존 사장의 연결이 그대로이고, 신청자 쪽은 아무것도 진행되지 않았는지 확인한다. */
    private void assertNothingChanged(LinkedPlaceFixture fixture) {
        assertThat(fixture.linkedBusinessInfo().getVerificationStatus())
                .isEqualTo(BusinessVerificationStatus.APPROVED);
        assertThat(fixture.linkedBusinessInfo().getBusinessNumber())
                .isEqualTo(PREVIOUS_BUSINESS_NUMBER);
        assertThat(fixture.linkedBusinessInfo().getPlace())
                .isSameAs(fixture.place());
        assertThat(fixture.promotion().getStatus())
                .isEqualTo(PromotionStatus.ACTIVE);
        assertThat(fixture.previousOwner().getRole())
                .isEqualTo(UserRole.BUSINESS);
        assertThat(fixture.applicant().getRole())
                .isEqualTo(UserRole.USER);

        // 국세청 진위확인과 이미지 이동 전에 막혀야 다시 시도할 때 이미지를 재업로드하지 않는다.
        verify(ntsBusinessVerificationClient, never())
                .verify(anyString(), any(), anyString());
        verifyNoInteractions(fileService, businessPromotionRepository);
        verify(businessInfoRepository, never()).saveAndFlush(any());
        verify(businessInfoRepository, never()).flush();
    }

    private void assertRejectedAsPlaceAlreadyLinked(LinkedPlaceFixture fixture) {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessVerificationService.verifyBusiness(
                        USER_ID,
                        verifyRequest()
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(
                        BusinessVerificationErrorCode
                                .BUSINESS_PLACE_ALREADY_LINKED
                );

        assertNothingChanged(fixture);
    }

    @Test
    void verifyBusiness_WhenLinkedBusinessIsActive_ThrowsPlaceAlreadyLinked() {
        LinkedPlaceFixture fixture = linkedPlaceFixture();

        when(ntsBusinessVerificationClient.getBusinessStatus(PREVIOUS_BUSINESS_NUMBER))
                .thenReturn(previousOwnerStatus("01"));

        assertRejectedAsPlaceAlreadyLinked(fixture);
    }

    @Test
    void verifyBusiness_WhenLinkedBusinessIsSuspended_ThrowsPlaceAlreadyLinked() {
        LinkedPlaceFixture fixture = linkedPlaceFixture();

        when(ntsBusinessVerificationClient.getBusinessStatus(PREVIOUS_BUSINESS_NUMBER))
                .thenReturn(previousOwnerStatus("02"));

        assertRejectedAsPlaceAlreadyLinked(fixture);
    }

    @Test
    void verifyBusiness_WhenLinkedBusinessStatusLookupFails_ThrowsPlaceAlreadyLinked() {
        LinkedPlaceFixture fixture = linkedPlaceFixture();

        // 확인되지 않은 상태에서는 남의 연결을 풀지 않는다.
        when(ntsBusinessVerificationClient.getBusinessStatus(PREVIOUS_BUSINESS_NUMBER))
                .thenThrow(new GeneralException(
                        BusinessVerificationErrorCode.BUSINESS_VERIFICATION_API_ERROR
                ));

        assertRejectedAsPlaceAlreadyLinked(fixture);
    }

    @Test
    void verifyBusiness_WhenLinkedBusinessIsClosed_RevokesPreviousOwnerAndLinksNewOwner() {
        // given
        LinkedPlaceFixture fixture = linkedPlaceFixture();

        when(ntsBusinessVerificationClient.getBusinessStatus(PREVIOUS_BUSINESS_NUMBER))
                .thenReturn(previousOwnerStatus("03"));

        when(businessPromotionRepository.findForUpdateByPlaceId(LINKED_PLACE_ID))
                .thenReturn(Optional.of(fixture.promotion()));

        // 기존 사장은 이 사업장 하나뿐이라 해제 후 승인된 사업장이 남지 않는다.
        when(businessInfoRepository.existsByUser_IdAndVerificationStatus(
                PREVIOUS_OWNER_ID,
                BusinessVerificationStatus.APPROVED
        )).thenReturn(false);

        when(ntsBusinessVerificationClient.verify(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME
        )).thenReturn(new NtsBusinessVerifyDTO.Result(
                BUSINESS_NUMBER,
                "01",
                "확인되었습니다.",
                ntsStatus("01")
        ));

        when(fileService.moveToDirectory(
                "temp/test.jpg",
                ImageDirectory.BUSINESS
        )).thenReturn("businesses/test.jpg");

        when(businessInfoRepository.saveAndFlush(any(BusinessInfo.class)))
                .thenAnswer(invocation -> {
                    BusinessInfo businessInfo =
                            invocation.getArgument(0, BusinessInfo.class);
                    ReflectionTestUtils.setField(businessInfo, "id", BUSINESS_INFO_ID);
                    return businessInfo;
                });

        // when
        BusinessVerifyResDTO response =
                businessVerificationService.verifyBusiness(
                        USER_ID,
                        verifyRequest()
                );

        // then: 기존 사장의 연결이 해제되고 홍보글이 내려가며 권한이 되돌아간다.
        assertThat(fixture.linkedBusinessInfo().getVerificationStatus())
                .isEqualTo(BusinessVerificationStatus.REVOKED);
        assertThat(fixture.linkedBusinessInfo().getBusinessNumber()).isNull();
        assertThat(fixture.linkedBusinessInfo().getPlace()).isNull();
        assertThat(fixture.promotion().getStatus())
                .isEqualTo(PromotionStatus.DELETED);
        assertThat(fixture.previousOwner().getRole())
                .isEqualTo(UserRole.USER);

        // 기존 행의 place_id 를 비운 UPDATE 가 새 행의 INSERT 보다 먼저 나가야 한다.
        InOrder inOrder = inOrder(businessInfoRepository);
        inOrder.verify(businessInfoRepository).flush();
        inOrder.verify(businessInfoRepository).saveAndFlush(any(BusinessInfo.class));

        // 새 사장이 같은 장소로 승인된다.
        ArgumentCaptor<BusinessInfo> captor =
                ArgumentCaptor.forClass(BusinessInfo.class);
        verify(businessInfoRepository).saveAndFlush(captor.capture());

        BusinessInfo savedBusinessInfo = captor.getValue();
        assertThat(savedBusinessInfo.getUser()).isSameAs(fixture.applicant());
        assertThat(savedBusinessInfo.getPlace()).isSameAs(fixture.place());
        assertThat(savedBusinessInfo.getBusinessNumber()).isEqualTo(BUSINESS_NUMBER);
        assertThat(savedBusinessInfo.getVerificationStatus())
                .isEqualTo(BusinessVerificationStatus.APPROVED);

        assertThat(response.role()).isEqualTo(UserRole.BUSINESS);
        assertThat(response.businessInfoId()).isEqualTo(BUSINESS_INFO_ID);
        assertThat(fixture.applicant().getRole()).isEqualTo(UserRole.BUSINESS);

        // 기존 장소를 재사용하므로 새 장소를 만들지 않는다.
        verifyNoInteractions(regionRepository);
        verify(placeRepository, never()).save(any());
    }

    @Test
    void verifyBusiness_WhenLinkedBusinessIsClosedButApplicantFailsVerification_PropagatesApplicantError() {
        // given
        LinkedPlaceFixture fixture = linkedPlaceFixture();

        when(ntsBusinessVerificationClient.getBusinessStatus(PREVIOUS_BUSINESS_NUMBER))
                .thenReturn(previousOwnerStatus("03"));

        when(businessPromotionRepository.findForUpdateByPlaceId(LINKED_PLACE_ID))
                .thenReturn(Optional.of(fixture.promotion()));

        when(businessInfoRepository.existsByUser_IdAndVerificationStatus(
                PREVIOUS_OWNER_ID,
                BusinessVerificationStatus.APPROVED
        )).thenReturn(false);

        when(ntsBusinessVerificationClient.verify(
                BUSINESS_NUMBER,
                OPENING_DATE,
                REPRESENTATIVE_NAME
        )).thenReturn(new NtsBusinessVerifyDTO.Result(
                BUSINESS_NUMBER,
                "02",
                "확인할 수 없습니다.",
                null
        ));

        when(ntsBusinessVerificationClient.getBusinessStatus(BUSINESS_NUMBER))
                .thenReturn(ntsStatus("01"));

        // when
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessVerificationService.verifyBusiness(
                        USER_ID,
                        verifyRequest()
                )
        );

        // then: 신청자 본인 확인 실패가 그대로 전파된다.
        // 해제는 같은 트랜잭션 안에서 일어나므로 예외로 함께 롤백된다(통합 테스트에서 확인).
        assertThat(exception.getErrorCode())
                .isEqualTo(BusinessVerificationErrorCode.BUSINESS_VERIFICATION_FAILED);

        verifyNoInteractions(fileService);
        verify(businessInfoRepository, never()).saveAndFlush(any());
        assertThat(fixture.applicant().getRole()).isEqualTo(UserRole.USER);
    }

    // ───────── 회원 탈퇴 시 전체 해제 (작업 3) ─────────

    @Test
    void revokeAllByUserId_RevokesEveryApprovedBusinessAndDemotesUser() {
        // given
        User owner = User.builder()
                .id(PREVIOUS_OWNER_ID)
                .role(UserRole.BUSINESS)
                .build();

        Place placeWithPromotion = Place.builder()
                .externalPlaceId("place-1")
                .source(PlaceSource.KAKAO)
                .name("사업장 1")
                .build();
        ReflectionTestUtils.setField(placeWithPromotion, "id", 101L);

        Place placeWithoutPromotion = Place.builder()
                .externalPlaceId("place-2")
                .source(PlaceSource.KAKAO)
                .name("사업장 2")
                .build();
        ReflectionTestUtils.setField(placeWithoutPromotion, "id", 102L);

        BusinessInfo first = BusinessInfo.builder()
                .id(21L)
                .user(owner)
                .place(placeWithPromotion)
                .businessNumber("1111111111")
                .verificationStatus(BusinessVerificationStatus.APPROVED)
                .build();

        BusinessInfo second = BusinessInfo.builder()
                .id(22L)
                .user(owner)
                .place(placeWithoutPromotion)
                .businessNumber("2222222222")
                .verificationStatus(BusinessVerificationStatus.APPROVED)
                .build();

        BusinessPromotion activePromotion = BusinessPromotion.builder()
                .id(31L)
                .place(placeWithPromotion)
                .user(owner)
                .promotionCategory(PromotionCategory.FOOD)
                .status(PromotionStatus.ACTIVE)
                .build();

        when(businessInfoRepository
                .findAllByUser_IdAndVerificationStatusOrderByIdDesc(
                        PREVIOUS_OWNER_ID,
                        BusinessVerificationStatus.APPROVED
                )).thenReturn(List.of(second, first));

        when(businessPromotionRepository.findForUpdateByPlaceId(101L))
                .thenReturn(Optional.of(activePromotion));
        when(businessPromotionRepository.findForUpdateByPlaceId(102L))
                .thenReturn(Optional.empty());

        when(businessInfoRepository.existsByUser_IdAndVerificationStatus(
                PREVIOUS_OWNER_ID,
                BusinessVerificationStatus.APPROVED
        )).thenReturn(true, false);

        // when
        businessVerificationService.revokeAllByUserId(PREVIOUS_OWNER_ID);

        // then
        for (BusinessInfo businessInfo : List.of(first, second)) {
            assertThat(businessInfo.getVerificationStatus())
                    .isEqualTo(BusinessVerificationStatus.REVOKED);
            assertThat(businessInfo.getBusinessNumber()).isNull();
            assertThat(businessInfo.getPlace()).isNull();
        }

        assertThat(activePromotion.getStatus()).isEqualTo(PromotionStatus.DELETED);
        assertThat(owner.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void revokeAllByUserId_WhenUserHasNoApprovedBusiness_DoesNothing() {
        when(businessInfoRepository
                .findAllByUser_IdAndVerificationStatusOrderByIdDesc(
                        USER_ID,
                        BusinessVerificationStatus.APPROVED
                )).thenReturn(List.of());

        businessVerificationService.revokeAllByUserId(USER_ID);

        verifyNoInteractions(businessPromotionRepository, userRepository);
        verify(businessInfoRepository, never())
                .existsByUser_IdAndVerificationStatus(any(), any());
    }
}
