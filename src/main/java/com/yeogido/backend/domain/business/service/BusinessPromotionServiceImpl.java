package com.yeogido.backend.domain.business.service;

import com.yeogido.backend.domain.business.dto.request.BusinessPromotionRequest;
import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;
import com.yeogido.backend.domain.business.entity.BusinessOperatingDay;
import com.yeogido.backend.domain.business.entity.BusinessPromotion;
import com.yeogido.backend.domain.business.entity.BusinessPromotionHashtag;
import com.yeogido.backend.domain.business.entity.BusinessPromotionImage;
import com.yeogido.backend.domain.business.enums.DayOfWeek;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import com.yeogido.backend.domain.business.exception.BusinessPromotionErrorCode;
import com.yeogido.backend.domain.business.repository.BusinessOperatingDayRepository;
import com.yeogido.backend.domain.business.repository.BusinessPromotionHashtagRepository;
import com.yeogido.backend.domain.business.repository.BusinessPromotionImageRepository;
import com.yeogido.backend.domain.business.repository.BusinessPromotionRepository;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.hashtag.exception.HashtagErrorCode;
import com.yeogido.backend.domain.hashtag.repository.HashtagRepository;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessPromotionServiceImpl implements BusinessPromotionService {

    private final BusinessPromotionRepository businessPromotionRepository;
    private final BusinessOperatingDayRepository businessOperatingDayRepository;
    private final BusinessPromotionHashtagRepository businessPromotionHashtagRepository;
    private final BusinessPromotionImageRepository businessPromotionImageRepository;
    private final PlaceRepository placeRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final HashtagRepository hashtagRepository;

    // TODO: 인증 연동 후 현재 로그인 사용자 ID로 변경
    private static final Long MOCK_USER_ID = 1L;

    @Override
    @Transactional
    public BusinessPromotionResponse.Register registerBusinessPromotion(
            BusinessPromotionRequest.Register request
    ) {
        User user = userRepository.getReferenceById(MOCK_USER_ID);

        validateDuplicateBusinessHours(request.businessHours());
        validateDuplicateImageSortOrders(request.images());

        Place place = getOrCreatePlace(request.place());

        BusinessPromotion businessPromotion =
                createOrReactivatePromotion(place, user, request);

        saveBusinessHours(businessPromotion, request.businessHours());

        savePromotionImages(businessPromotion, request.images());

        savePromotionHashtags(businessPromotion, request.hashtagIds());

        return BusinessPromotionResponse.Register.builder()
                .promotionId(businessPromotion.getId())
                .build();
    }

    private Place getOrCreatePlace(
            BusinessPromotionRequest.Place request
    ) {
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

    private void saveBusinessHours(
            BusinessPromotion businessPromotion,
            List<BusinessPromotionRequest.BusinessHour> businessHours) {
        List<BusinessOperatingDay> operatingDays = businessHours.stream()
                .map(businessHour -> BusinessOperatingDay.builder()
                        .promotion(businessPromotion)
                        .dayOfWeek(parseDayOfWeek(businessHour.dayOfWeek()))
                        .openTime(businessHour.openTime())
                        .closeTime(businessHour.closeTime())
                        .build()
                )
                .toList();
        businessOperatingDayRepository.saveAll(operatingDays);
    }

    private DayOfWeek parseDayOfWeek(String dayOfWeek) {
        try {
            return DayOfWeek.valueOf(dayOfWeek);
        } catch (IllegalArgumentException exception) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }
    }

    private void savePromotionImages(
            BusinessPromotion businessPromotion,
            List<BusinessPromotionRequest.Image> images
    ) {
        List<BusinessPromotionImage> promotionImages = images.stream()
                .map(image -> BusinessPromotionImage.builder()
                        .promotion(businessPromotion)
                        .imageKey(image.imageKey())
                        .sortOrder(image.sortOrder())
                        .build()
                )
                .toList();

        businessPromotionImageRepository.saveAll(promotionImages);
    }

    private void savePromotionHashtags(
            BusinessPromotion businessPromotion,
            List<Long> hashtagIds
    ) {
        if (hashtagIds == null || hashtagIds.isEmpty()) {
            return;
        }

        List<Long> distinctHashtagIds = hashtagIds.stream()
                .distinct()
                .toList();

        List<Hashtag> hashtags =
                hashtagRepository.findAllById(distinctHashtagIds);

        if (hashtags.size() != distinctHashtagIds.size()) {
            throw new GeneralException(
                    HashtagErrorCode.HASHTAG_NOT_FOUND
            );
        }

        List<BusinessPromotionHashtag> promotionHashtags = hashtags.stream()
                .map(hashtag -> BusinessPromotionHashtag.builder()
                        .promotion(businessPromotion)
                        .hashtag(hashtag)
                        .build())
                .toList();

        businessPromotionHashtagRepository.saveAll(promotionHashtags);
    }

    private BusinessPromotion createOrReactivatePromotion(
            Place place,
            User user,
            BusinessPromotionRequest.Register request
    ) {
        BusinessPromotion existingPromotion = businessPromotionRepository
                .findByPlaceId(place.getId())
                .orElse(null);

        if (existingPromotion == null) {
            BusinessPromotion newPromotion = BusinessPromotion.builder()
                    .place(place)
                    .user(user)
                    .shortDescription(request.shortDescription())
                    .ownerComment(request.ownerComment())
                    .promotionCategory(request.promotionCategory())
                    .phoneNumber(request.phoneNumber())
                    .snsAccount(request.snsAccount())
                    .status(PromotionStatus.ACTIVE)
                    .build();

            return businessPromotionRepository.save(newPromotion);
        }

        if (existingPromotion.getStatus() == PromotionStatus.ACTIVE) {
            throw new GeneralException(
                    BusinessPromotionErrorCode.BUSINESS_PROMOTION_ALREADY_EXISTS
            );
        }

        clearPromotionDetails(existingPromotion.getId());

        existingPromotion.reactivate(
                user,
                request.shortDescription(),
                request.ownerComment(),
                request.promotionCategory(),
                request.phoneNumber(),
                request.snsAccount()
        );

        return businessPromotionRepository.save(existingPromotion);
    }

    private void clearPromotionDetails(Long promotionId) {
        businessOperatingDayRepository.deleteAllByPromotion_Id(promotionId);
        businessPromotionImageRepository.deleteAllByPromotion_Id(promotionId);
        businessPromotionHashtagRepository.deleteAllByPromotion_Id(promotionId);

        businessPromotionRepository.flush();
    }

    private void validateDuplicateBusinessHours(
            List<BusinessPromotionRequest.BusinessHour> businessHours
    ) {
        Set<DayOfWeek> days = new HashSet<>();

        for (BusinessPromotionRequest.BusinessHour businessHour : businessHours) {
            DayOfWeek dayOfWeek = parseDayOfWeek(
                    businessHour.dayOfWeek()
            );

            if (!days.add(dayOfWeek)) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_REQUEST
                );
            }
        }
    }

    private void validateDuplicateImageSortOrders(
            List<BusinessPromotionRequest.Image> images
    ) {
        Set<Integer> sortOrders = new HashSet<>();

        for (BusinessPromotionRequest.Image image : images) {
            if (!sortOrders.add(image.sortOrder())) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_REQUEST
                );
            }
        }
    }
}
