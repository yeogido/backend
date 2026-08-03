package com.yeogido.backend.domain.business.service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.business.converter.BusinessPromotionConverter;
import com.yeogido.backend.domain.business.dto.request.BusinessPromotionRequest;
import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;
import com.yeogido.backend.domain.business.entity.*;
import com.yeogido.backend.domain.business.enums.DayOfWeek;
import com.yeogido.backend.domain.business.enums.PromotionCategory;
import com.yeogido.backend.domain.business.enums.PromotionSortType;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import com.yeogido.backend.domain.business.exception.BusinessPromotionErrorCode;
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
import com.yeogido.backend.domain.place.entity.QPlaceLike;
import com.yeogido.backend.domain.place.repository.PlaceLikeRepository;
import com.yeogido.backend.domain.region.entity.QRegion;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.BusinessInfoRepository;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessPromotionServiceImpl implements BusinessPromotionService {

    private final BusinessPromotionRepository businessPromotionRepository;
    private final BusinessOperatingDayRepository businessOperatingDayRepository;
    private final BusinessPromotionHashtagRepository businessPromotionHashtagRepository;
    private final BusinessPromotionImageRepository businessPromotionImageRepository;
    private final PlaceLikeRepository placeLikeRepository;
    private final UserRepository userRepository;
    private final HashtagRepository hashtagRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final RegionRepository regionRepository;
    private final FileService fileService;
    private final S3Service s3Service;

    private final JPAQueryFactory queryFactory;
    private final QBusinessPromotion qPromotion =
            QBusinessPromotion.businessPromotion;

    private final QPlaceLike qPlaceLike =
            QPlaceLike.placeLike;

    private final NumberExpression<Long> likeCountExpression =
            qPlaceLike.id.count();

    @Override
    @Transactional
    public BusinessPromotionResponse.Register registerBusinessPromotion(
            Long userId,
            BusinessPromotionRequest.Register request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(
                        UserErrorCode.USER_NOT_FOUND
                ));

        if (user.getRole() != UserRole.BUSINESS) {
            throw new GeneralException(
                    BusinessPromotionErrorCode
                            .BUSINESS_PROMOTION_VERIFICATION_REQUIRED
            );
        }

        BusinessInfo businessInfo = businessInfoRepository
                .findByIdAndUser_IdAndVerificationStatus(
                        request.businessInfoId(),
                        userId,
                        BusinessVerificationStatus.APPROVED
                )
                .orElseThrow(() -> new GeneralException(
                        BusinessPromotionErrorCode
                                .BUSINESS_PROMOTION_VERIFICATION_REQUIRED
                ));

        Place place = businessInfo.getPlace();

        if (place == null) {
            throw new GeneralException(
                    BusinessPromotionErrorCode
                            .BUSINESS_PROMOTION_VERIFICATION_REQUIRED
            );
        }

        validateDuplicateBusinessHours(request.businessHours());
        validateDuplicateImageSortOrders(request.images());

        BusinessPromotion businessPromotion =
                createOrReactivatePromotion(place, user, request);

        saveBusinessHours(
                businessPromotion,
                request.businessHours()
        );

        savePromotionImages(
                businessPromotion,
                request.images()
        );

        savePromotionHashtags(
                businessPromotion,
                request.hashtagIds()
        );

        return BusinessPromotionConverter.toRegisterResponse(
                businessPromotion
        );
    }

    @Override
    @Transactional
    public BusinessPromotionResponse.Update updateBusinessPromotion(
            Long userId,
            Long promotionId,
            BusinessPromotionRequest.Update request
    ) {
        validateUpdateRequest(request);

        BusinessPromotion promotion =
                getActiveBusinessPromotion(promotionId);

        validateBusinessPromotionOwner(
                promotion,
                userId,
                BusinessPromotionErrorCode.BUSINESS_PROMOTION_FORBIDDEN_UPDATE
        );

        if (request.businessHours() != null) {
            validateDuplicateBusinessHours(request.businessHours());
        }

        if (request.images() != null) {
            validateDuplicateImageSortOrders(request.images());
        }

        changePromotionPlaceIfRequested(
                promotion,
                request.businessInfoId(),
                userId
        );

        promotion.update(
                request.shortDescription(),
                request.ownerComment(),
                request.promotionCategory(),
                request.phoneNumber(),
                request.snsAccount()
        );

        boolean replacesDetails = false;

        if (request.businessHours() != null) {
            businessOperatingDayRepository
                    .deleteAllByPromotion_Id(promotionId);

            replacesDetails = true;
        }

        if (request.images() != null) {
            businessPromotionImageRepository
                    .deleteAllByPromotion_Id(promotionId);

            replacesDetails = true;
        }

        if (request.hashtagIds() != null) {
            businessPromotionHashtagRepository
                    .deleteAllByPromotion_Id(promotionId);

            replacesDetails = true;
        }

        if (replacesDetails) {
            businessPromotionRepository.flush();
        }

        if (request.businessHours() != null) {
            saveBusinessHours(
                    promotion,
                    request.businessHours()
            );
        }

        if (request.images() != null) {
            savePromotionImages(
                    promotion,
                    request.images()
            );
        }

        if (request.hashtagIds() != null) {
            savePromotionHashtags(
                    promotion,
                    request.hashtagIds()
            );
        }

        return BusinessPromotionConverter.toUpdateResponse(promotion);
    }

    @Override
    @Transactional
    public void deleteBusinessPromotion(
            Long userId,
            Long promotionId
    ) {
        BusinessPromotion promotion =
                getActiveBusinessPromotion(promotionId);

        validateBusinessPromotionOwner(
                promotion,
                userId,
                BusinessPromotionErrorCode.BUSINESS_PROMOTION_FORBIDDEN_DELETE
        );

        promotion.delete();
    }

    @Override
    public BusinessPromotionResponse.Detail getBusinessPromotion(
            Long userId,
            Long promotionId
    ) {
        BusinessPromotion promotion = businessPromotionRepository.findByIdAndStatus(promotionId, PromotionStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(
                        BusinessPromotionErrorCode.BUSINESS_PROMOTION_NOT_FOUND
                ));

        Place place = promotion.getPlace();

        BusinessPromotionResponse.PlaceInfo placeInfo =
                BusinessPromotionConverter.toPlaceInfo(place);

        List<BusinessPromotionResponse.BusinessHourInfo> businessHours =
                businessOperatingDayRepository
                        .findAllByPromotion_Id(promotionId)
                        .stream()
                        .sorted(Comparator.comparing(BusinessOperatingDay::getDayOfWeek))
                        .map(BusinessPromotionConverter::toBusinessHourInfo)
                        .toList();

        List<BusinessPromotionResponse.ImageInfo> images =
                businessPromotionImageRepository
                        .findAllByPromotion_IdOrderBySortOrderAsc(promotionId)
                        .stream()
                        .map(promotionImage ->
                                BusinessPromotionConverter.toImageInfo(
                                        promotionImage,
                                        s3Service.getImageUrl(
                                                promotionImage.getImageKey()
                                        )
                                )
                        )
                        .toList();

        List<String> hashtags =
                businessPromotionHashtagRepository
                        .findAllByPromotion_IdOrderByHashtag_IdAsc(promotionId)
                        .stream()
                        .map(promotionHashtag ->
                                promotionHashtag.getHashtag().getHashtagName()
                        )
                        .toList();

        long likeCount = placeLikeRepository.countByPlaceId(place.getId());

        boolean isLiked =
                userId != null && placeLikeRepository.existsByUserIdAndPlaceId(
                        userId,
                        place.getId()
                );

        String profileImageUrl = s3Service.getImageUrl(
                promotion.getUser().getProfileImage()
        );

        return BusinessPromotionConverter.toDetailResponse(
                promotion,
                placeInfo,
                businessHours,
                hashtags,
                images,
                likeCount,
                isLiked,
                profileImageUrl
        );
    }

    private BusinessPromotion getActiveBusinessPromotion(
            Long promotionId
    ) {
        return businessPromotionRepository
                .findByIdAndStatus(
                        promotionId,
                        PromotionStatus.ACTIVE
                )
                .orElseThrow(() -> new GeneralException(
                        BusinessPromotionErrorCode.BUSINESS_PROMOTION_NOT_FOUND
                ));
    }

    private void changePromotionPlaceIfRequested(
            BusinessPromotion promotion,
            Long businessInfoId,
            Long userId
    ) {
        if (businessInfoId == null) {
            return;
        }

        BusinessInfo businessInfo = businessInfoRepository
                .findByIdAndUser_IdAndVerificationStatus(
                        businessInfoId,
                        userId,
                        BusinessVerificationStatus.APPROVED
                )
                .orElseThrow(() -> new GeneralException(
                        BusinessPromotionErrorCode
                                .BUSINESS_PROMOTION_VERIFICATION_REQUIRED
                ));

        Place requestedPlace = businessInfo.getPlace();

        if (requestedPlace == null) {
            throw new GeneralException(
                    BusinessPromotionErrorCode
                            .BUSINESS_PROMOTION_VERIFICATION_REQUIRED
            );
        }

        if (Objects.equals(
                promotion.getPlace().getId(),
                requestedPlace.getId()
        )) {
            return;
        }

        businessPromotionRepository
                .findByPlaceId(requestedPlace.getId())
                .ifPresent(existingPromotion -> {
                    throw new GeneralException(
                            BusinessPromotionErrorCode
                                    .BUSINESS_PROMOTION_ALREADY_EXISTS
                    );
                });

        promotion.changePlace(requestedPlace);
    }

    private void validateBusinessPromotionOwner(
            BusinessPromotion promotion,
            Long userId,
            BusinessPromotionErrorCode errorCode
    ) {
        if (!Objects.equals(
                promotion.getUser().getId(),
                userId
        )) {
            throw new GeneralException(errorCode);
        }
    }

    private void validateUpdateRequest(
            BusinessPromotionRequest.Update request
    ) {
        boolean hasUpdateValue =
                request.businessInfoId() != null
                        || request.shortDescription() != null
                        || request.ownerComment() != null
                        || request.businessHours() != null
                        || request.snsAccount() != null
                        || request.phoneNumber() != null
                        || request.hashtagIds() != null
                        || request.promotionCategory() != null
                        || request.images() != null;

        if (!hasUpdateValue) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }

        if (isBlank(request.shortDescription())
                || isBlank(request.ownerComment())
                || isBlank(request.snsAccount())
                || isBlank(request.phoneNumber())) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }
    }

    private boolean isBlank(String value) {
        return value != null && value.isBlank();
    }

    @Override
    public CursorResponse<BusinessPromotionResponse.MySummary>
    getMyBusinessPromotions(
            Long userId,
            LocalDateTime cursorValue,
            Long cursorId,
            Integer size
    ) {
        boolean firstPage =
                cursorValue == null && cursorId == null;

        if (!firstPage && (cursorValue == null || cursorId == null)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }

        Pageable pageable = PageRequest.of(0, size + 1);

        List<BusinessPromotion> promotions;

        if (firstPage) {
            promotions =
                    businessPromotionRepository
                            .findByUserIdAndStatusOrderByCreatedAtDescIdDesc(
                                    userId,
                                    PromotionStatus.ACTIVE,
                                    pageable
                            );
        } else {
            promotions =
                    businessPromotionRepository.findMyPromotionsAfterCursor(
                            userId,
                            PromotionStatus.ACTIVE,
                            cursorValue,
                            cursorId,
                            pageable
                    );
        }

        boolean hasNext = promotions.size() > size;

        List<BusinessPromotion> pageItems =
                hasNext
                        ? promotions.subList(0, size)
                        : promotions;

        List<Long> promotionIds = pageItems.stream()
                .map(BusinessPromotion::getId)
                .toList();

        Map<Long, BusinessPromotionImage> thumbnailImageMap =
                promotionIds.isEmpty()
                        ? Map.of()
                        : businessPromotionImageRepository
                        .findAllByPromotion_IdInAndSortOrder(
                                promotionIds,
                                1
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                image -> image.getPromotion().getId(),
                                image -> image
                        ));

        List<Long> placeIds = pageItems.stream()
                .map(promotion -> promotion.getPlace().getId())
                .toList();

        Map<Long, Long> likeCountMap =
                placeIds.isEmpty()
                        ? Map.of()
                        : placeLikeRepository.countByPlaceIds(placeIds)
                        .stream()
                        .collect(Collectors.toMap(
                                PlaceLikeRepository.PlaceLikeCount::getPlaceId,
                                PlaceLikeRepository.PlaceLikeCount::getLikeCount
                        ));

        List<BusinessPromotionResponse.MySummary> items =
                pageItems.stream()
                        .map(promotion -> {
                            Place place = promotion.getPlace();

                            BusinessPromotionImage thumbnailImage =
                                    thumbnailImageMap.get(promotion.getId());

                            String thumbnailImageUrl =
                                    thumbnailImage == null
                                            ? null
                                            : s3Service.getImageUrl(
                                            thumbnailImage.getImageKey()
                                    );

                            long likeCount =
                                    likeCountMap.getOrDefault(
                                            place.getId(),
                                            0L
                                    );

                            return BusinessPromotionConverter.toMySummaryResponse(
                                    promotion,
                                    thumbnailImageUrl,
                                    likeCount
                            );
                        })
                        .toList();

        LocalDateTime nextCursorValue = null;
        Long nextCursorId = null;

        if (hasNext && !pageItems.isEmpty()) {
            BusinessPromotion lastPromotion =
                    pageItems.get(pageItems.size() - 1);

            nextCursorValue = lastPromotion.getCreatedAt();
            nextCursorId = lastPromotion.getId();
        }

        return CursorResponse.of(
                items,
                nextCursorValue,
                nextCursorId,
                hasNext
        );
    }

    @Override
    public CursorResponse<BusinessPromotionResponse.Summary>
    getBusinessPromotions(
            Long userId,
            String cursorValue,
            Long cursorId,
            Integer size,
            Long regionId,
            PromotionCategory category,
            PromotionSortType sort
    ) {
        BooleanBuilder condition = new BooleanBuilder();

        condition.and(
                qPromotion.status.eq(PromotionStatus.ACTIVE)
        );

        validateRegionExists(regionId);
        condition.and(regionIdEq(regionId));

        if (category != null) {
            condition.and(qPromotion.promotionCategory.eq(category));
        }

        boolean firstPage = cursorValue == null && cursorId == null;

        if (!firstPage && (cursorValue == null || cursorId == null)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }

        List<BusinessPromotion> promotions = switch (sort) {
            case RECOMMEND -> {
                if (firstPage) {
                    yield findRecommendedPromotions(
                            condition,
                            size
                    );
                }

                RecommendedCursor recommendedCursor = parseRecommendedCursor(cursorValue);

                yield findRecommendedPromotionsAfterCursor(
                        condition,
                        recommendedCursor.priority(),
                        recommendedCursor.createdAt(),
                        cursorId,
                        size
                );
            }

            case SAVED -> {
                if (firstPage) {
                    yield findSavedPromotions(
                            condition,
                            size
                    );
                }

                SavedCursor savedCursor =
                        parseSavedCursor(cursorValue);

                yield findSavedPromotionsAfterCursor(
                        condition,
                        savedCursor.likeCount(),
                        savedCursor.createdAt(),
                        cursorId,
                        size
                );
            }
        };

        boolean hasNext = promotions.size() > size;

        List<BusinessPromotion> pageItems =
                hasNext
                        ? promotions.subList(0, size)
                        : promotions;

        List<Long> placeIds = pageItems.stream()
                .map(promotion -> promotion.getPlace().getId())
                .toList();

        Set<Long> likedPlaceIds =
                userId == null || placeIds.isEmpty()
                        ? Set.of()
                        : new HashSet<>(
                        placeLikeRepository.findLikedPlaceIds(
                                userId,
                                placeIds
                        )
                );

        List<Long> promotionIds = pageItems.stream()
                .map(BusinessPromotion::getId)
                .toList();

        Map<Long, BusinessPromotionImage> thumbnailImageMap =
                promotionIds.isEmpty()
                        ? Map.of()
                        : businessPromotionImageRepository
                        .findAllByPromotion_IdInAndSortOrder(
                                promotionIds,
                                1
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                image -> image.getPromotion().getId(),
                                image -> image
                        ));

        Map<Long, Long> likeCountMap =
                placeIds.isEmpty()
                        ? Map.of()
                        : placeLikeRepository.countByPlaceIds(placeIds)
                        .stream()
                        .collect(Collectors.toMap(
                                PlaceLikeRepository.PlaceLikeCount::getPlaceId,
                                PlaceLikeRepository.PlaceLikeCount::getLikeCount
                        ));

        List<BusinessPromotionResponse.Summary> items =
                pageItems.stream()
                        .map(promotion -> {
                            Place place = promotion.getPlace();

                            BusinessPromotionImage thumbnailImage =
                                    thumbnailImageMap.get(promotion.getId());

                            String thumbnailImageUrl =
                                    thumbnailImage == null ? null : s3Service.getImageUrl(thumbnailImage.getImageKey());

                            long likeCount =
                                    likeCountMap.getOrDefault(place.getId(), 0L);

                            boolean isLiked = likedPlaceIds.contains(place.getId());

                            return BusinessPromotionConverter.toSummaryResponse(
                                    promotion,
                                    thumbnailImageUrl,
                                    likeCount,
                                    isLiked
                            );
                        })
                        .toList();

        String nextCursorValue = null;
        Long nextCursorId = null;

        if (hasNext && !pageItems.isEmpty()) {
            BusinessPromotion lastPromotion = pageItems.get(pageItems.size() - 1);

            switch (sort) {
                case RECOMMEND ->
                        nextCursorValue =
                                lastPromotion.getRecommendationPriority()
                                        + "|"
                                        + lastPromotion.getCreatedAt();

                case SAVED -> {
                    Long lastLikeCount =
                            likeCountMap.getOrDefault(
                                    lastPromotion.getPlace().getId(),
                                    0L
                            );

                    nextCursorValue =
                            lastLikeCount
                                    + "|"
                                    + lastPromotion.getCreatedAt();
                }
            }
            nextCursorId = lastPromotion.getId();
        }

        return CursorResponse.of(
                items,
                nextCursorValue,
                nextCursorId,
                hasNext
        );
    }

    private void validateRegionExists(Long regionId) {
        if (regionId != null && !regionRepository.existsById(regionId)) {
            throw new GeneralException(
                    RegionErrorCode.REGION_NOT_FOUND
            );
        }
    }

    private BooleanExpression regionIdEq(Long regionId) {
        if (regionId == null) {
            return null;
        }

        QRegion filterRegion = new QRegion("filterRegion");

        return qPromotion.place.region.id.in(
                JPAExpressions
                        .select(filterRegion.id)
                        .from(filterRegion)
                        .where(
                                filterRegion.id.eq(regionId)
                                        .or(filterRegion.parent.id.eq(regionId))
                        )
        );
    }

    private List<BusinessPromotion> findRecommendedPromotions(
            BooleanBuilder condition,
            Integer size
    ) {
        return queryFactory
                .selectFrom(qPromotion)
                .join(qPromotion.place).fetchJoin()
                .join(qPromotion.place.region).fetchJoin()
                .where(condition)
                .orderBy(
                        qPromotion.recommendationPriority.desc(),
                        qPromotion.createdAt.desc(),
                        qPromotion.id.desc()
                )
                .limit(size + 1L)
                .fetch();
    }

    private List<BusinessPromotion> findRecommendedPromotionsAfterCursor(
            BooleanBuilder condition,
            Integer cursorPriority,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size
    ) {
        BooleanExpression cursorCondition =
                qPromotion.recommendationPriority.lt(cursorPriority)
                        .or(
                                qPromotion.recommendationPriority
                                        .eq(cursorPriority)
                                        .and(
                                                qPromotion.createdAt.lt(
                                                        cursorCreatedAt
                                                )
                                        )
                        )
                        .or(
                                qPromotion.recommendationPriority
                                        .eq(cursorPriority)
                                        .and(
                                                qPromotion.createdAt.eq(
                                                        cursorCreatedAt
                                                )
                                        )
                                        .and(qPromotion.id.lt(cursorId))
                        );

        return queryFactory
                .selectFrom(qPromotion)
                .join(qPromotion.place).fetchJoin()
                .join(qPromotion.place.region).fetchJoin()
                .where(condition, cursorCondition)
                .orderBy(
                        qPromotion.recommendationPriority.desc(),
                        qPromotion.createdAt.desc(),
                        qPromotion.id.desc()
                )
                .limit(size + 1L)
                .fetch();
    }

    private List<BusinessPromotion> findSavedPromotions(
            BooleanBuilder condition,
            Integer size
    ) {
        return queryFactory
                .select(qPromotion)
                .from(qPromotion)
                .join(qPromotion.place).fetchJoin()
                .join(qPromotion.place.region).fetchJoin()
                .leftJoin(qPlaceLike)
                .on(qPlaceLike.place.id.eq(qPromotion.place.id))
                .where(condition)
                .groupBy(qPromotion.id)
                .orderBy(
                        likeCountExpression.desc(),
                        qPromotion.createdAt.desc(),
                        qPromotion.id.desc()
                )
                .limit(size + 1L)
                .fetch();
    }

    private List<BusinessPromotion> findSavedPromotionsAfterCursor(
            BooleanBuilder condition,
            Long cursorLikeCount,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size
    ) {
        BooleanExpression cursorCondition =
                likeCountExpression.lt(cursorLikeCount)
                        .or(
                                likeCountExpression.eq(cursorLikeCount)
                                        .and(
                                                qPromotion.createdAt.lt(
                                                        cursorCreatedAt
                                                )
                                        )
                        )
                        .or(
                                likeCountExpression.eq(cursorLikeCount)
                                        .and(
                                                qPromotion.createdAt.eq(
                                                        cursorCreatedAt
                                                )
                                        )
                                        .and(qPromotion.id.lt(cursorId))
                        );

        return queryFactory
                .select(qPromotion)
                .from(qPromotion)
                .join(qPromotion.place).fetchJoin()
                .join(qPromotion.place.region).fetchJoin()
                .leftJoin(qPlaceLike)
                .on(qPlaceLike.place.id.eq(qPromotion.place.id))
                .where(condition)
                .groupBy(qPromotion.id)
                .having(cursorCondition)
                .orderBy(
                        likeCountExpression.desc(),
                        qPromotion.createdAt.desc(),
                        qPromotion.id.desc()
                )
                .limit(size + 1L)
                .fetch();
    }

    private SavedCursor parseSavedCursor(String cursorValue) {
        String[] cursorParts = cursorValue.split("\\|", 2);

        if (cursorParts.length != 2) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }

        try {
            return new SavedCursor(
                    Long.parseLong(cursorParts[0]),
                    LocalDateTime.parse(cursorParts[1])
            );
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }
    }

    private RecommendedCursor parseRecommendedCursor(
            String cursorValue
    ) {
        String[] cursorParts = cursorValue.split("\\|", 2);

        if (cursorParts.length != 2) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }

        try {
            return new RecommendedCursor(
                    Integer.parseInt(cursorParts[0]),
                    LocalDateTime.parse(cursorParts[1])
            );
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }
    }

    private record RecommendedCursor(
            Integer priority,
            LocalDateTime createdAt
    ) {
    }

    private record SavedCursor(
            Long likeCount,
            LocalDateTime createdAt
    ) { }

    private void saveBusinessHours(
            BusinessPromotion businessPromotion,
            List<BusinessPromotionRequest.BusinessHour> businessHours) {
        List<BusinessOperatingDay> operatingDays = businessHours.stream()
                .map(businessHour ->
                        BusinessPromotionConverter.toBusinessOperatingDay(
                                businessPromotion,
                                businessHour,
                                parseDayOfWeek(businessHour.dayOfWeek())
                        )
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
                .map(this::movePromotionImage)
                .map(image ->
                        BusinessPromotionConverter.toBusinessPromotionImage(
                                businessPromotion,
                                image
                        )
                )
                .toList();

        businessPromotionImageRepository.saveAll(promotionImages);
    }

    private BusinessPromotionRequest.Image movePromotionImage(BusinessPromotionRequest.Image image) {
        return new BusinessPromotionRequest.Image(
                fileService.moveToDirectory(image.imageKey(), ImageDirectory.BUSINESS),
                image.sortOrder()
        );
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
                .map(hashtag ->
                        BusinessPromotionConverter.toBusinessPromotionHashtag(
                                businessPromotion,
                                hashtag
                        )
                )
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
            BusinessPromotion newPromotion =
                    BusinessPromotionConverter.toBusinessPromotion(
                            place,
                            user,
                            request
                    );

            return businessPromotionRepository.save(newPromotion);
        }

        if (existingPromotion.getStatus() == PromotionStatus.ACTIVE) {
            throw new GeneralException(
                    BusinessPromotionErrorCode.BUSINESS_PROMOTION_ALREADY_EXISTS
            );
        }

        if (!Objects.equals(
                existingPromotion.getUser().getId(),
                user.getId()
        )) {
            throw new GeneralException(
                    BusinessPromotionErrorCode.BUSINESS_PROMOTION_ALREADY_EXISTS
            );
        }

        clearPromotionDetails(existingPromotion.getId());

        existingPromotion.reactivate(
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

        if (!sortOrders.contains(1)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_REQUEST
            );
        }
    }
}
