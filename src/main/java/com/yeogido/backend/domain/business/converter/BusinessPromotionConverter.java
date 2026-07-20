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
}