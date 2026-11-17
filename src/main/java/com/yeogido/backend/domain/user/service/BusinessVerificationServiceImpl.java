package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.place.dto.request.PlaceRequest;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.client.NtsBusinessVerificationClient;
import com.yeogido.backend.domain.user.converter.BusinessVerificationConverter;
import com.yeogido.backend.domain.user.dto.BusinessInfoResDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyReqDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyResDTO;
import com.yeogido.backend.domain.user.dto.nts.NtsBusinessVerifyDTO;
import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import com.yeogido.backend.domain.user.enums.NtsBusinessStatus;
import com.yeogido.backend.domain.user.exception.BusinessVerificationErrorCode;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.BusinessInfoRepository;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessVerificationServiceImpl
        implements BusinessVerificationService {

    /** 국세청 진위확인 응답 {@code valid} 값 중 "일치" */
    private static final String VALID_BUSINESS = "01";

    private final UserRepository userRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final PlaceRepository placeRepository;
    private final RegionRepository regionRepository;
    private final NtsBusinessVerificationClient ntsBusinessVerificationClient;
    private final FileService fileService;

    @Override
    @Transactional
    public BusinessVerifyResDTO verifyBusiness(
            Long userId,
            BusinessVerifyReqDTO request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new GeneralException(
                                UserErrorCode.USER_NOT_FOUND
                        )
                );

        Optional<BusinessInfo> existingBusinessInfo =
                businessInfoRepository.findByBusinessNumber(
                        request.businessNumber()
                );

        validateExistingBusinessInfo(
                existingBusinessInfo,
                userId
        );

        validateKakaoPlaceSource(request.place());

        // 장소 하나에는 사업자 정보 하나만 연결된다(business_info.place_id unique).
        // 저장 시점의 제약 위반으로 알게 되면 원인과 다른 '사업자 번호 중복'으로
        // 안내되고 이미지도 이미 옮겨진 뒤라, 국세청 호출과 이미지 이동 전에 확인한다.
        Optional<Place> existingPlace =
                findExistingKakaoPlace(request.place());

        validatePlaceNotLinked(existingPlace);

        NtsBusinessVerifyDTO.Result verificationResult =
                ntsBusinessVerificationClient.verify(
                        request.businessNumber(),
                        request.openingDate(),
                        request.representativeName()
                );

        validateAuthenticity(
                request.businessNumber(),
                verificationResult
        );
        validateActiveBusiness(verificationResult);

        BusinessVerifyReqDTO movedRequest =
                moveRegistrationImage(request);

        Place place = existingPlace.orElseGet(() ->
                createPlace(movedRequest.place())
        );

        LocalDateTime verifiedAt = LocalDateTime.now();

        BusinessInfo businessInfo;

        if (existingBusinessInfo.isPresent()) {
            businessInfo = existingBusinessInfo.get();

            businessInfo.reverifyAndConnectPlace(
                    place,
                    movedRequest.openingDate(),
                    movedRequest.representativeName(),
                    movedRequest.registrationImageKey(),
                    movedRequest.businessName(),
                    movedRequest.businessAddress(),
                    verifiedAt
            );
        } else {
            businessInfo =
                    BusinessVerificationConverter.toBusinessInfo(
                            user,
                            place,
                            movedRequest,
                            verifiedAt
                    );
        }

        BusinessInfo savedBusinessInfo =
                saveBusinessInfo(businessInfo);

        user.promoteToBusiness();

        return BusinessVerificationConverter.toVerifyResponse(
                user,
                savedBusinessInfo
        );
    }

    @Override
    public List<BusinessInfoResDTO> getApprovedBusinesses(
            Long userId
    ) {
        if (!userRepository.existsById(userId)) {
            throw new GeneralException(
                    UserErrorCode.USER_NOT_FOUND
            );
        }

        return businessInfoRepository
                .findAllByUser_IdAndVerificationStatusOrderByIdDesc(
                        userId,
                        BusinessVerificationStatus.APPROVED
                )
                .stream()
                .map(BusinessVerificationConverter::toBusinessInfoResponse)
                .toList();
    }

    private void validateKakaoPlaceSource(
            PlaceRequest placeRequest
    ) {
        if (!PlaceSource.KAKAO.name().equals(placeRequest.source())) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }
    }

    private Optional<Place> findExistingKakaoPlace(
            PlaceRequest placeRequest
    ) {
        return placeRepository.findBySourceAndExternalPlaceId(
                PlaceSource.KAKAO,
                placeRequest.externalPlaceId()
        );
    }

    private void validatePlaceNotLinked(Optional<Place> existingPlace) {
        existingPlace
                .filter(place ->
                        businessInfoRepository.existsByPlace_Id(place.getId())
                )
                .ifPresent(place -> {
                    throw new GeneralException(
                            BusinessVerificationErrorCode
                                    .BUSINESS_PLACE_ALREADY_LINKED
                    );
                });
    }

    private Place createPlace(PlaceRequest request) {
        PlaceSource source = parsePlaceSource(request.source());

        Region region = regionRepository
                .findById(request.regionId())
                .orElseThrow(() -> new GeneralException(
                        RegionErrorCode.REGION_NOT_FOUND
                ));

        Place newPlace = Place.builder()
                .region(region)
                .externalPlaceId(request.externalPlaceId())
                .source(source)
                .name(request.name())
                .categoryGroupCode(request.categoryGroupCode())
                .roadAddress(request.roadAddress())
                .lotAddress(request.lotAddress())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();

        return placeRepository.save(newPlace);
    }

    private PlaceSource parsePlaceSource(String source) {
        try {
            return PlaceSource.valueOf(source);
        } catch (IllegalArgumentException exception) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }
    }

    private void validateExistingBusinessInfo(
            Optional<BusinessInfo> existingBusinessInfo,
            Long userId
    ) {
        if (existingBusinessInfo.isEmpty()) {
            return;
        }

        BusinessInfo businessInfo = existingBusinessInfo.get();

        boolean ownedByCurrentUser = Objects.equals(
                businessInfo.getUser().getId(),
                userId
        );

        boolean alreadyConnectedToPlace =
                businessInfo.getPlace() != null;

        if (!ownedByCurrentUser || alreadyConnectedToPlace) {
            throw new GeneralException(
                    BusinessVerificationErrorCode
                            .BUSINESS_NUMBER_DUPLICATED
            );
        }
    }

    /**
     * 진위확인 결과를 검증한다.
     * <p>
     * 진위확인이 실패하면 상태 조회를 한 번 더 해서, 계속사업자가 아니면 영업 상태 안내
     * (미등록 4041, 휴업 4042, 폐업 4043)를 정보 불일치(4002)보다 우선해서 돌려준다.
     */
    private void validateAuthenticity(
            String businessNumber,
            NtsBusinessVerifyDTO.Result verificationResult
    ) {
        if (VALID_BUSINESS.equals(verificationResult.valid())) {
            return;
        }

        NtsBusinessStatus businessStatus = NtsBusinessStatus.from(
                ntsBusinessVerificationClient.getBusinessStatus(
                        businessNumber
                )
        );

        if (!businessStatus.isActive()) {
            throw new GeneralException(businessStatus.toErrorCode());
        }

        throw new GeneralException(
                BusinessVerificationErrorCode.BUSINESS_VERIFICATION_FAILED
        );
    }

    /**
     * 진위확인은 성공했지만 영업 상태가 계속사업자가 아니면 상태에 맞는 에러코드로 거절한다.
     */
    private void validateActiveBusiness(
            NtsBusinessVerifyDTO.Result verificationResult
    ) {
        NtsBusinessStatus businessStatus =
                NtsBusinessStatus.from(verificationResult.status());

        if (!businessStatus.isActive()) {
            throw new GeneralException(businessStatus.toErrorCode());
        }
    }

    private BusinessInfo saveBusinessInfo(
            BusinessInfo businessInfo
    ) {
        try {
            return businessInfoRepository.saveAndFlush(businessInfo);
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_NUMBER_DUPLICATED
            );
        }
    }

    private BusinessVerifyReqDTO moveRegistrationImage(BusinessVerifyReqDTO request) {
        return new BusinessVerifyReqDTO(
                request.businessNumber(),
                request.openingDate(),
                request.representativeName(),
                fileService.moveToDirectory(
                        request.registrationImageKey(),
                        ImageDirectory.BUSINESS
                ),
                request.businessName(),
                request.businessAddress(),
                request.place()
        );
    }
}
