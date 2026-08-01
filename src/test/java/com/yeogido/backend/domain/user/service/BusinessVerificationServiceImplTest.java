package com.yeogido.backend.domain.user.service;

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
import com.yeogido.backend.domain.user.repository.BusinessInfoRepository;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
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
}