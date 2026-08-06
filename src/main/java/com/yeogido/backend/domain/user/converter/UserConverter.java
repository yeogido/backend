package com.yeogido.backend.domain.user.converter;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentLike;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseLike;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.entity.PlaceLike;
import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.service.UserServiceImpl;
import com.yeogido.backend.domain.user.enums.LikeCategory;
import com.yeogido.backend.global.util.DistanceUtil;

import java.util.List;

public class UserConverter {

    private UserConverter() {
    }

    public static UserResDTO.Profile toProfile(User user, String profileImageUrl) {
        return new UserResDTO.Profile(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRegion().getName(),
                user.getBirthYear(),
                user.getRole(),
                profileImageUrl
        );
    }

    public static UserResDTO.LikedResponse toLikedResponse(
            CourseLike like,
            List<String> hashtags,
            String thumbnailImage
    ) {
        Course course = like.getCourse();

        return new UserResDTO.LikedResponse(
                course.getId(),
                LikeCategory.COURSE,
                course.getTitle(),
                null,
                thumbnailImage,
                course.getDurationType().name(),
                course.getTransportType(),
                course.getCompanionType(),
                null,
                null,
                course.getRegion().getName(),
                null,
                hashtags,
                like.getCreatedAt().toString()
        );
    }


    public static UserResDTO.LikedResponse toLikedResponse(
            ContentLike like,
            List<String> hashtags,
            String thumbnailImage
    ) {
        Content content = like.getContent();

        return new UserResDTO.LikedResponse(
                content.getId(),
                LikeCategory.CONTENT,
                content.getTitle(),
                null,
                thumbnailImage,
                null,
                null,
                null,
                content.getStartDate(),
                content.getEndDate(),
                content.getPlace().getRegion().getName(),
                null,
                hashtags,

                like.getCreatedAt().toString()
        );
    }

    public static UserResDTO.LikedResponse toLikedResponse(
            PlaceLike like,
            Double latitude,
            Double longitude
    ) {
        Place place = like.getPlace();

        Double distance = null;

        if (latitude != null && longitude != null) {
            distance = DistanceUtil.calculate(
                    latitude,
                    longitude,
                    place.getLatitude().doubleValue(),
                    place.getLongitude().doubleValue()
            );
        }

        return new UserResDTO.LikedResponse(
                place.getId(),
                LikeCategory.PLACE,
                place.getName(),
                place.getExternalPlaceId(),
                null,
                null,
                null,
                null,
                null,
                null,
                place.getRegion().getFullName(),
                distance,
                List.of(),
                like.getCreatedAt().toString()
        );
    }

}
