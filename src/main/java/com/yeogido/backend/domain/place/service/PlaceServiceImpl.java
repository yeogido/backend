package com.yeogido.backend.domain.place.service;

import com.yeogido.backend.domain.business.entity.BusinessPromotion;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import com.yeogido.backend.domain.business.repository.BusinessPromotionRepository;
import com.yeogido.backend.domain.content.converter.ContentConverter;
import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentPublicationStatus;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.course.converter.CourseConverter;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.repository.CourseItemRepository;
import com.yeogido.backend.domain.place.dto.request.PlaceLikeRequest;
import com.yeogido.backend.domain.place.dto.response.PlaceResponse;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceLikeSourceType;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.exception.PlaceErrorCode;
import com.yeogido.backend.domain.place.repository.PlaceLikeRepository;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceServiceImpl implements PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceLikeRepository placeLikeRepository;
    private final RegionRepository regionRepository;
    private final CourseItemRepository courseItemRepository;
    private final ContentRepository contentRepository;
    private final BusinessPromotionRepository businessPromotionRepository;

    @Override
    @Transactional
    public PlaceResponse.PlaceLikeRes createPlaceLike(
            Long userId,
            Long placeId,
            PlaceLikeRequest request
    ) {
        getPlace(placeId);

        validatePlaceLikeSource(
                placeId,
                request.sourceType(),
                request.sourceId()
        );

        placeLikeRepository.insertIgnore(
                placeId,
                userId,
                request.sourceType().name(),
                request.sourceId()
        );

        return new PlaceResponse.PlaceLikeRes(
                placeLikeRepository.existsByUserIdAndPlaceId(userId, placeId),
                placeLikeRepository.countByPlaceId(placeId)
        );
    }

    @Override
    @Transactional
    public PlaceResponse.PlaceLikeRes deletePlaceLike(Long userId, Long placeId) {
        getPlace(placeId);

        placeLikeRepository.findByUserIdAndPlaceId(userId, placeId)
                .ifPresent(placeLikeRepository::delete);

        return new PlaceResponse.PlaceLikeRes(
                false,
                placeLikeRepository.countByPlaceId(placeId)
        );
    }

    @Override
    public Map<String, Place> getPlaceMap(List<CourseReqDTO.CourseItemCreateReq> items) {
        Set<String> externalPlaceIds = items.stream()
                .filter(item -> item.type() == CourseItemType.PLACE)
                .map(CourseReqDTO.CourseItemCreateReq::externalPlaceId)
                .collect(Collectors.toSet());

        if (externalPlaceIds.isEmpty()) {
            return new HashMap<>();
        }

        return placeRepository.findBySourceAndExternalPlaceIdIn(PlaceSource.KAKAO, externalPlaceIds)
                .stream()
                .collect(Collectors.toMap(Place::getExternalPlaceId, Function.identity()));
    }

    @Override
    @Transactional
    public Place getOrCreatePlace(CourseReqDTO.CourseItemCreateReq item, Map<String, Place> placeMap) {
        Place place = placeMap.get(item.externalPlaceId());
        if (place != null) {
            return place;
        }

        Region placeRegion = findRegionByAddress(item.roadAddress(), item.lotAddress());
        Place newPlace = placeRepository.save(CourseConverter.toPlace(item, placeRegion));

        placeMap.put(newPlace.getExternalPlaceId(), newPlace);
        return newPlace;
    }

    @Override
    @Transactional
    public Place getOrCreatePlace(ContentReqDTO.PlaceReq request) {

        PlaceSource source = request.source();

        Optional<Place> optionalPlace =
                placeRepository.findBySourceAndExternalPlaceId(
                        source,
                        request.externalPlaceId()
                );

        if (optionalPlace.isPresent()) {
            Place place = optionalPlace.get();

            return place;
        }

        Region region = findRegionByAddress(
                request.roadAddress(),
                request.lotAddress()
        );

        Place newPlace = ContentConverter.toPlace(request, region);

        newPlace = placeRepository.save(newPlace);

        return newPlace;
    }

    private void validatePlaceLikeSource(
            Long placeId,
            PlaceLikeSourceType sourceType,
            Long sourceId
    ) {
        switch (sourceType) {
            case COURSE_ITEM -> validateCourseItemSource(placeId, sourceId);
            case CONTENT -> validateContentSource(placeId, sourceId);
            case PROMOTION -> validatePromotionSource(placeId, sourceId);
        }
    }

    private void validateContentSource(
            Long placeId,
            Long contentId
    ) {
        Content content = contentRepository.findById(contentId)
                .filter(found -> found.getPublicationStatus()
                        == ContentPublicationStatus.PUBLISHED)
                .orElseThrow(
                        () -> new GeneralException(
                                PlaceErrorCode.INVALID_PLACE_LIKE_SOURCE
                        )
                );

        if (!content.getPlace().getId().equals(placeId)) {
            throw new GeneralException(
                    PlaceErrorCode.INVALID_PLACE_LIKE_SOURCE
            );
        }
    }

    private void validateCourseItemSource(
            Long placeId,
            Long courseItemId
    ) {
        CourseItem courseItem = courseItemRepository.findById(courseItemId)
                .orElseThrow(
                        () -> new GeneralException(
                                PlaceErrorCode.INVALID_PLACE_LIKE_SOURCE
                        )
                );

        if (courseItem.getCourse().getDeletedAt() != null
                || courseItem.getItemType() != CourseItemType.PLACE
                || courseItem.getPlace() == null
                || !courseItem.getPlace().getId().equals(placeId)) {
            throw new GeneralException(
                    PlaceErrorCode.INVALID_PLACE_LIKE_SOURCE
            );
        }
    }

    private void validatePromotionSource(
            Long placeId,
            Long promotionId
    ) {
        BusinessPromotion promotion =
                businessPromotionRepository.findByIdAndStatus(
                                promotionId,
                                PromotionStatus.ACTIVE
                        )
                        .orElseThrow(
                                () -> new GeneralException(
                                        PlaceErrorCode.INVALID_PLACE_LIKE_SOURCE
                                )
                        );

        if (!promotion.getPlace().getId().equals(placeId)) {
            throw new GeneralException(
                    PlaceErrorCode.INVALID_PLACE_LIKE_SOURCE
            );
        }
    }

    private Region findRegionByAddress(String roadAddress, String lotAddress) {
        String address = StringUtils.hasText(roadAddress) ? roadAddress : lotAddress;
        if (!StringUtils.hasText(address)) {
            throw new GeneralException(RegionErrorCode.REGION_NOT_FOUND);
        }

        String[] addressParts = address.trim().split("\\s+");
        if (addressParts.length < 2) {
            throw new GeneralException(RegionErrorCode.REGION_NOT_FOUND);
        }

        String regionName = addressParts[0];
        String subRegionName = addressParts[1];

        Region region = regionRepository.findByFullName(regionName)
                .or(() -> regionRepository.findByName(regionName))
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));

        return regionRepository.findByParentAndName(region, subRegionName)
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));
    }

    private Place getPlace(Long placeId) {
        return placeRepository.findById(placeId)
                .orElseThrow(
                        () -> new GeneralException(
                                PlaceErrorCode.PLACE_NOT_FOUND
                        )
                );
    }
}
