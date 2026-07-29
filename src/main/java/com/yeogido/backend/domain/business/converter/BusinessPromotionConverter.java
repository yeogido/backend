package com.yeogido.backend.domain.business.converter;

import com.yeogido.backend.domain.business.dto.request.BusinessPromotionRequest;
import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;
import com.yeogido.backend.domain.business.entity.BusinessOperatingDay;
import com.yeogido.backend.domain.business.entity.BusinessPromotion;
import com.yeogido.backend.domain.business.entity.BusinessPromotionHashtag;
import com.yeogido.backend.domain.business.entity.BusinessPromotionImage;
import com.yeogido.backend.domain.business.enums.DayOfWeek;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.user.entity.User;

import java.util.List;

public final class BusinessPromotionConverter {

    private BusinessPromotionConverter() {
    }

    public static Place toPlace(
            BusinessPromotionRequest.Place request,
            PlaceSource source,
            Region region
    ) {
        return Place.builder()
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
    }

    public static BusinessPromotion toBusinessPromotion(
            Place place,
            User user,
            BusinessPromotionRequest.Register request
    ) {
        return BusinessPromotion.builder()
                .place(place)
                .user(user)
                .shortDescription(request.shortDescription())
                .ownerComment(request.ownerComment())
                .promotionCategory(request.promotionCategory())
                .phoneNumber(request.phoneNumber())
                .snsAccount(request.snsAccount())
                .status(PromotionStatus.ACTIVE)
                .build();
    }

    public static BusinessOperatingDay toBusinessOperatingDay(
            BusinessPromotion promotion,
            BusinessPromotionRequest.BusinessHour businessHour,
            DayOfWeek dayOfWeek
    ) {
        return BusinessOperatingDay.builder()
                .promotion(promotion)
                .dayOfWeek(dayOfWeek)
                .openTime(businessHour.openTime())
                .closeTime(businessHour.closeTime())
                .build();
    }

    public static BusinessPromotionImage toBusinessPromotionImage(
            BusinessPromotion promotion,
            BusinessPromotionRequest.Image image
    ) {
        return BusinessPromotionImage.builder()
                .promotion(promotion)
                .imageKey(image.imageKey())
                .sortOrder(image.sortOrder())
                .build();
    }

    public static BusinessPromotionHashtag toBusinessPromotionHashtag(
            BusinessPromotion promotion,
            Hashtag hashtag
    ) {
        return BusinessPromotionHashtag.builder()
                .promotion(promotion)
                .hashtag(hashtag)
                .build();
    }

    public static BusinessPromotionResponse.Register toRegisterResponse(
            BusinessPromotion promotion
    ) {
        return BusinessPromotionResponse.Register.builder()
                .promotionId(promotion.getId())
                .build();
    }

    public static BusinessPromotionResponse.Update toUpdateResponse(
            BusinessPromotion promotion
    ) {
        return BusinessPromotionResponse.Update.builder()
                .promotionId(promotion.getId())
                .build();
    }

    public static BusinessPromotionResponse.PlaceInfo toPlaceInfo(
            Place place
    ) {
        return BusinessPromotionResponse.PlaceInfo.builder()
                .placeId(place.getId())
                .name(place.getName())
                .categoryGroupCode(place.getCategoryGroupCode())
                .roadAddress(place.getRoadAddress())
                .lotAddress(place.getLotAddress())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .regionId(place.getRegion().getId())
                .regionName(place.getRegion().getName())
                .build();
    }

    public static BusinessPromotionResponse.BusinessHourInfo toBusinessHourInfo(
            BusinessOperatingDay operatingDay
    ) {
        return BusinessPromotionResponse.BusinessHourInfo.builder()
                .dayOfWeek(operatingDay.getDayOfWeek().name())
                .openTime(operatingDay.getOpenTime())
                .closeTime(operatingDay.getCloseTime())
                .build();
    }

    public static BusinessPromotionResponse.ImageInfo toImageInfo(
            BusinessPromotionImage promotionImage,
            String imageUrl
    ) {
        return BusinessPromotionResponse.ImageInfo.builder()
                .imageUrl(imageUrl)
                .sortOrder(promotionImage.getSortOrder())
                .build();
    }

    public static BusinessPromotionResponse.Detail toDetailResponse(
            BusinessPromotion promotion,
            BusinessPromotionResponse.PlaceInfo placeInfo,
            List<BusinessPromotionResponse.BusinessHourInfo> businessHours,
            List<String> hashtags,
            List<BusinessPromotionResponse.ImageInfo> images,
            long likeCount,
            boolean isLiked,
            String profileImageUrl
    ) {
        return BusinessPromotionResponse.Detail.builder()
                .promotionId(promotion.getId())
                .place(placeInfo)
                .promotionCategory(promotion.getPromotionCategory())
                .shortDescription(promotion.getShortDescription())
                .ownerComment(promotion.getOwnerComment())
                .businessHours(businessHours)
                .snsAccount(promotion.getSnsAccount())
                .phoneNumber(promotion.getPhoneNumber())
                .hashtags(hashtags)
                .images(images)
                .author(toAuthorResponse(promotion.getUser(), profileImageUrl))
                .likeCount(likeCount)
                .isLiked(isLiked)
                .createdAt(promotion.getCreatedAt())
                .updatedAt(promotion.getUpdatedAt())
                .build();
    }

    public static BusinessPromotionResponse.Summary toSummaryResponse(
            BusinessPromotion promotion,
            String thumbnailImageUrl,
            long likeCount,
            boolean isLiked
    ) {
        Place place = promotion.getPlace();

        return BusinessPromotionResponse.Summary.builder()
                .promotionId(promotion.getId())
                .placeId(place.getId())
                .placeName(place.getName())
                .promotionCategory(promotion.getPromotionCategory())
                .roadAddress(place.getRoadAddress())
                .regionId(place.getRegion().getId())
                .regionName(place.getRegion().getName())
                .thumbnailImageUrl(thumbnailImageUrl)
                .shortDescription(promotion.getShortDescription())
                .likeCount(likeCount)
                .isLiked(isLiked)
                .createdAt(promotion.getCreatedAt())
                .build();
    }

    public static BusinessPromotionResponse.MySummary toMySummaryResponse(
            BusinessPromotion promotion,
            String thumbnailImageUrl,
            long likeCount
    ) {
        Place place = promotion.getPlace();

        return BusinessPromotionResponse.MySummary.builder()
                .promotionId(promotion.getId())
                .placeId(place.getId())
                .placeName(place.getName())
                .promotionCategory(promotion.getPromotionCategory())
                .roadAddress(place.getRoadAddress())
                .thumbnailImageUrl(thumbnailImageUrl)
                .shortDescription(promotion.getShortDescription())
                .status(promotion.getStatus().name())
                .likeCount(likeCount)
                .createdAt(promotion.getCreatedAt())
                .updatedAt(promotion.getUpdatedAt())
                .build();
    }

    public static BusinessPromotionResponse.Author toAuthorResponse(
            User user,
            String profileImageUrl
    ) {
        return BusinessPromotionResponse.Author.builder()
                .nickname(user.getNickname())
                .profileImageUrl(profileImageUrl)
                .build();
    }
}