package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.client.NtsBusinessVerificationClient;
import com.yeogido.backend.domain.user.dto.BusinessInfoResDTO;
import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.BusinessInfoRepository;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** 인증 사업장 목록의 "홍보 중" 표시(hasActivePromotion)를 확인한다. */
@ExtendWith(MockitoExtension.class)
class BusinessVerificationServiceImplApprovedBusinessesTest {

    private static final Long USER_ID = 1L;

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
    void getApprovedBusinesses_MarksOnlyBusinessesWithActivePromotion() {
        // given
        BusinessInfo withPromotion = approvedBusinessInfo(3L, "3333333333");
        BusinessInfo deletedPromotionOnly = approvedBusinessInfo(2L, "2222222222");
        BusinessInfo withoutPromotion = approvedBusinessInfo(1L, "1111111111");

        when(userRepository.existsById(USER_ID)).thenReturn(true);

        when(businessInfoRepository
                .findAllByUser_IdAndVerificationStatusOrderByIdDesc(
                        USER_ID,
                        BusinessVerificationStatus.APPROVED
                )).thenReturn(List.of(withPromotion, deletedPromotionOnly, withoutPromotion));

        // 활성 홍보글이 있는 사업장만 ID 집합으로 돌아온다 (삭제된 홍보글만 있는 2번은 빠진다).
        when(businessInfoRepository.findIdsWithActivePromotion(
                USER_ID,
                BusinessVerificationStatus.APPROVED
        )).thenReturn(Set.of(3L));

        // when
        List<BusinessInfoResDTO> responses =
                businessVerificationService.getApprovedBusinesses(USER_ID);

        // then
        assertThat(responses)
                .extracting(BusinessInfoResDTO::businessInfoId, BusinessInfoResDTO::hasActivePromotion)
                .containsExactly(
                        tuple(3L, true),
                        tuple(2L, false),
                        tuple(1L, false)
                );

        assertThat(responses.get(0).businessNumber()).isEqualTo("3333333333");

        // 사업장이 여러 개여도 홍보글 여부 조회는 한 번이다.
        verify(businessInfoRepository, times(1))
                .findIdsWithActivePromotion(any(), any());
    }

    @Test
    void getApprovedBusinesses_WhenUserHasNoBusiness_DoesNotQueryPromotions() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);

        when(businessInfoRepository
                .findAllByUser_IdAndVerificationStatusOrderByIdDesc(
                        USER_ID,
                        BusinessVerificationStatus.APPROVED
                )).thenReturn(List.of());

        List<BusinessInfoResDTO> responses =
                businessVerificationService.getApprovedBusinesses(USER_ID);

        assertThat(responses).isEmpty();
        verify(businessInfoRepository, never())
                .findIdsWithActivePromotion(anyLong(), any());
    }

    @Test
    void getApprovedBusinesses_WhenUserDoesNotExist_ThrowsUserNotFound() {
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> businessVerificationService.getApprovedBusinesses(USER_ID)
        );

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(businessInfoRepository);
    }

    private static BusinessInfo approvedBusinessInfo(Long id, String businessNumber) {
        return BusinessInfo.builder()
                .id(id)
                .businessNumber(businessNumber)
                .businessName("사업장 " + id)
                .businessAddress("부산 수영구 광안해변로 " + id)
                .representativeName("홍길동")
                .verificationStatus(BusinessVerificationStatus.APPROVED)
                .verifiedAt(LocalDateTime.of(2026, 7, 26, 15, 30))
                .build();
    }
}
