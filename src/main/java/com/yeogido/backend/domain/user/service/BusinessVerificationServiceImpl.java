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

    private static final String VALID_BUSINESS = "01";
    private static final String ACTIVE_BUSINESS = "01";

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

        Place place = getOrCreatePlace(movedRequest.place());

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

    private Place getOrCreatePlace(PlaceRequest request) {
        PlaceSource source = parsePlaceSource(request.source());

        return placeRepository
                .findBySourceAndExternalPlaceId(
                        source,
                        request.externalPlaceId()
                )
                .orElseGet(() -> {
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
                });
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

    private void validateAuthenticity(
            String businessNumber,
            NtsBusinessVerifyDTO.Result verificationResult
    ) {
        if (VALID_BUSINESS.equals(verificationResult.valid())) {
            return;
        }

        NtsBusinessVerifyDTO.Status businessStatus =
                ntsBusinessVerificationClient.getBusinessStatus(
                        businessNumber
                );

        if (!ACTIVE_BUSINESS.equals(
                businessStatus.businessStatusCode()
        )) {
            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_NOT_ACTIVE
            );
        }

        throw new GeneralException(
                BusinessVerificationErrorCode.BUSINESS_VERIFICATION_FAILED
        );
    }

    private void validateActiveBusiness(
            NtsBusinessVerifyDTO.Result verificationResult
    ) {
        NtsBusinessVerifyDTO.Status status = verificationResult.status();

        if (
                status == null
                        || !ACTIVE_BUSINESS.equals(status.businessStatusCode())
        ) {
            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_NOT_ACTIVE
            );
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
