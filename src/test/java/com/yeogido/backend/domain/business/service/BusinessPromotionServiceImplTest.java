package com.yeogido.backend.domain.business.service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.business.dto.request.BusinessPromotionRequest;
import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;
import com.yeogido.backend.domain.business.entity.BusinessPromotion;
import com.yeogido.backend.domain.business.enums.PromotionCategory;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import com.yeogido.backend.domain.business.repository.BusinessOperatingDayRepository;
import com.yeogido.backend.domain.business.repository.BusinessPromotionHashtagRepository;
import com.yeogido.backend.domain.business.repository.BusinessPromotionImageRepository;
import com.yeogido.backend.domain.business.repository.BusinessPromotionRepository;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.hashtag.exception.HashtagErrorCode;
import com.yeogido.backend.domain.hashtag.repository.HashtagRepository;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.repository.PlaceLikeRepository;
import com.yeogido.backend.domain.region.repository.RegionRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessPromotionServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long BUSINESS_INFO_ID = 10L;
    private static final Long PLACE_ID = 100L;
    private static final Long PROMOTION_ID = 1000L;

    @Mock private BusinessPromotionRepository businessPromotionRepository;
    @Mock private BusinessOperatingDayRepository businessOperatingDayRepository;
    @Mock private BusinessPromotionHashtagRepository businessPromotionHashtagRepository;
    @Mock private BusinessPromotionImageRepository businessPromotionImageRepository;
    @Mock private PlaceLikeRepository placeLikeRepository;
    @Mock private UserRepository userRepository;
    @Mock private HashtagRepository hashtagRepository;
    @Mock private BusinessInfoRepository businessInfoRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private FileService fileService;
    @Mock private S3Service s3Service;
    @Mock private JPAQueryFactory queryFactory;

    @InjectMocks
    private BusinessPromotionServiceImpl businessPromotionService;

    // ---------- 등록: 해시태그 검증은 이미지 이동 전에 ----------

    @Test
    void register_WhenHashtagDoesNotExist_ThrowsHashtagNotFoundBeforeMovingImages() {
        // given
        givenBusinessUserWithPlace();

        BusinessPromotionRequest.Register request = validRegister()
                .hashtagIds(List.of(1L, 2L))
                .build();

        when(hashtagRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(hashtag(1L)));

        // when
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessPromotionService.registerBusinessPromotion(USER_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(HashtagErrorCode.HASHTAG_NOT_FOUND);

        // 파일 이동이 한 번도 일어나지 않고, 홍보글도 저장되지 않는다.
        verifyNoInteractions(fileService);
        verify(businessPromotionRepository, never()).save(any());
        verify(businessPromotionImageRepository, never()).saveAll(any());
    }

    @Test
    void register_WhenHashtagsExist_MovesImagesAndSavesHashtags() {
        // given
        givenBusinessUserWithPlace();
        givenNewPromotionIsSaved();

        BusinessPromotionRequest.Register request = validRegister()
                .hashtagIds(List.of(1L, 1L, 2L))
                .build();

        when(hashtagRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(hashtag(1L), hashtag(2L)));
        when(fileService.moveToDirectory("temp/image.jpg", ImageDirectory.BUSINESS))
                .thenReturn("businesses/image.jpg");

        // when
        BusinessPromotionResponse.Register response =
                businessPromotionService.registerBusinessPromotion(USER_ID, request);

        // then
        assertThat(response.promotionId()).isEqualTo(PROMOTION_ID);
        verify(fileService).moveToDirectory("temp/image.jpg", ImageDirectory.BUSINESS);
        verify(businessOperatingDayRepository).saveAll(anyList());
        verify(businessPromotionImageRepository).saveAll(anyList());
        verify(businessPromotionHashtagRepository).saveAll(anyList());
    }

    // ---------- 등록: 영업시간 ----------

    @Test
    void register_WhenOpenTimeEqualsCloseTime_ThrowsInvalidRequestBeforeMovingImages() {
        // given
        givenBusinessUserWithPlace();

        BusinessPromotionRequest.Register request = validRegister()
                .businessHours(List.of(hour("MONDAY", "10:00", "10:00")))
                .build();

        // when
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessPromotionService.registerBusinessPromotion(USER_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(GeneralErrorCode.INVALID_REQUEST);
        verifyNoInteractions(fileService, hashtagRepository);
        verify(businessPromotionRepository, never()).save(any());
    }

    @Test
    void register_WhenOpenAndCloseAreBothMidnight_TreatsAsAllDayAndSucceeds() {
        // given
        givenBusinessUserWithPlace();
        givenNewPromotionIsSaved();
        when(fileService.moveToDirectory(anyString(), any())).thenReturn("businesses/image.jpg");

        BusinessPromotionRequest.Register request = validRegister()
                .businessHours(List.of(hour("MONDAY", "00:00", "00:00")))
                .build();

        // when
        BusinessPromotionResponse.Register response =
                businessPromotionService.registerBusinessPromotion(USER_ID, request);

        // then
        assertThat(response.promotionId()).isEqualTo(PROMOTION_ID);
        verify(businessOperatingDayRepository).saveAll(anyList());
    }

    @Test
    void register_WhenCloseTimeIsBeforeOpenTime_AllowsOvernightHours() {
        // given
        givenBusinessUserWithPlace();
        givenNewPromotionIsSaved();
        when(fileService.moveToDirectory(anyString(), any())).thenReturn("businesses/image.jpg");

        BusinessPromotionRequest.Register request = validRegister()
                .businessHours(List.of(hour("FRIDAY", "18:00", "02:00")))
                .build();

        // when
        BusinessPromotionResponse.Register response =
                businessPromotionService.registerBusinessPromotion(USER_ID, request);

        // then
        assertThat(response.promotionId()).isEqualTo(PROMOTION_ID);
        verify(businessOperatingDayRepository).saveAll(anyList());
    }

    // ---------- 수정 ----------

    @Test
    void update_WhenHashtagDoesNotExist_ThrowsHashtagNotFoundBeforeMovingImages() {
        // given
        givenActivePromotionOwnedByUser();

        BusinessPromotionRequest.Update request = BusinessPromotionRequest.Update.builder()
                .hashtagIds(List.of(1L, 2L))
                .images(List.of(new BusinessPromotionRequest.Image("temp/new.jpg", 1)))
                .build();

        when(hashtagRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(hashtag(1L)));

        // when
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessPromotionService.updateBusinessPromotion(USER_ID, PROMOTION_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(HashtagErrorCode.HASHTAG_NOT_FOUND);

        // 기존 이미지도 지워지지 않고, 새 이미지도 옮겨지지 않는다.
        verifyNoInteractions(fileService);
        verify(businessPromotionImageRepository, never()).deleteAllByPromotion_Id(anyLong());
        verify(businessPromotionHashtagRepository, never()).deleteAllByPromotion_Id(anyLong());
    }

    @Test
    void update_WhenOpenTimeEqualsCloseTime_ThrowsInvalidRequest() {
        // given
        givenActivePromotionOwnedByUser();

        BusinessPromotionRequest.Update request = BusinessPromotionRequest.Update.builder()
                .businessHours(List.of(hour("MONDAY", "09:30", "09:30")))
                .images(List.of(new BusinessPromotionRequest.Image("temp/new.jpg", 1)))
                .build();

        // when
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessPromotionService.updateBusinessPromotion(USER_ID, PROMOTION_ID, request)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(GeneralErrorCode.INVALID_REQUEST);
        verifyNoInteractions(fileService);
        verify(businessOperatingDayRepository, never()).deleteAllByPromotion_Id(anyLong());
    }

    @Test
    void update_WhenAllDayHours_Succeeds() {
        // given
        givenActivePromotionOwnedByUser();

        BusinessPromotionRequest.Update request = BusinessPromotionRequest.Update.builder()
                .businessHours(List.of(hour("MONDAY", "00:00", "00:00")))
                .build();

        // when
        BusinessPromotionResponse.Update response =
                businessPromotionService.updateBusinessPromotion(USER_ID, PROMOTION_ID, request);

        // then
        assertThat(response.promotionId()).isEqualTo(PROMOTION_ID);
        verify(businessOperatingDayRepository).deleteAllByPromotion_Id(PROMOTION_ID);
        verify(businessOperatingDayRepository).saveAll(anyList());
    }

    // ---------- fixtures ----------

    private void givenBusinessUserWithPlace() {
        User user = User.builder()
                .id(USER_ID)
                .role(UserRole.BUSINESS)
                .build();

        Place place = Place.builder()
                .id(PLACE_ID)
                .name("여기도 카페")
                .build();

        BusinessInfo businessInfo = BusinessInfo.builder()
                .id(BUSINESS_INFO_ID)
                .user(user)
                .place(place)
                .verificationStatus(BusinessVerificationStatus.APPROVED)
                .build();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(businessInfoRepository.findByIdAndUser_IdAndVerificationStatus(
                BUSINESS_INFO_ID, USER_ID, BusinessVerificationStatus.APPROVED
        )).thenReturn(Optional.of(businessInfo));
    }

    private void givenNewPromotionIsSaved() {
        when(businessPromotionRepository.findByPlaceId(PLACE_ID)).thenReturn(Optional.empty());
        when(businessPromotionRepository.save(any(BusinessPromotion.class)))
                .thenAnswer(invocation -> {
                    BusinessPromotion saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", PROMOTION_ID);
                    return saved;
                });
    }

    private void givenActivePromotionOwnedByUser() {
        User user = User.builder()
                .id(USER_ID)
                .role(UserRole.BUSINESS)
                .build();

        BusinessPromotion promotion = BusinessPromotion.builder()
                .id(PROMOTION_ID)
                .user(user)
                .place(Place.builder().id(PLACE_ID).build())
                .shortDescription("기존 소개글")
                .promotionCategory(PromotionCategory.CAFE)
                .phoneNumber("051-727-1660")
                .status(PromotionStatus.ACTIVE)
                .build();

        when(businessPromotionRepository.findByIdAndStatus(PROMOTION_ID, PromotionStatus.ACTIVE))
                .thenReturn(Optional.of(promotion));
    }

    private static BusinessPromotionRequest.Register.RegisterBuilder validRegister() {
        return BusinessPromotionRequest.Register.builder()
                .businessInfoId(BUSINESS_INFO_ID)
                .shortDescription("바다 뷰 카페")
                .businessHours(List.of(hour("MONDAY", "10:00", "22:00")))
                .phoneNumber("051-727-1660")
                .promotionCategory(PromotionCategory.CAFE)
                .images(List.of(new BusinessPromotionRequest.Image("temp/image.jpg", 1)));
    }

    private static BusinessPromotionRequest.BusinessHour hour(String day, String open, String close) {
        return new BusinessPromotionRequest.BusinessHour(
                day, LocalTime.parse(open), LocalTime.parse(close)
        );
    }

    private static Hashtag hashtag(Long id) {
        return Hashtag.builder()
                .id(id)
                .hashtagName("태그" + id)
                .build();
    }
}
