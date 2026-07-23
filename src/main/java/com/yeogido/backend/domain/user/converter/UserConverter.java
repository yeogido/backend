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

import java.util.List;

public class UserConverter {

    private UserConverter() {
    }

    public static UserResDTO.Profile toProfile(User user) {
        return new UserResDTO.Profile(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRegion().getName(),
                user.getBirthYear(),
                user.getRole(),
                user.getProfileImage()
        );
    }

    public static UserResDTO.LikedResponse toLikedResponse(
            CourseLike like,
            List<String> hashtags
    ) {
        Course course = like.getCourse();

        return new UserResDTO.LikedResponse(
                course.getId(),
                LikeCategory.COURSE,
                course.getTitle(),
                course.getThumbnailKey(),
                course.getDurationType().name(),
                null,
                null,
                course.getRegion().getName(),
                hashtags,
                like.getCreatedAt().toString()
        );
    }

    public static UserResDTO.LikedResponse toLikedResponse(
            ContentLike like,
            List<String> hashtags
    ) {
        Content content = like.getContent();

        return new UserResDTO.LikedResponse(
                content.getId(),
                LikeCategory.CONTENT,
                content.getTitle(),
                content.getThumbnailImage(),
                null,
                content.getStartDate(),
                content.getEndDate(),
                content.getPlace().getRegion().getName(),
                hashtags,
                like.getCreatedAt().toString()
        );
    }

    public static UserResDTO.LikedResponse toLikedResponse(
            PlaceLike like
    ) {
        Place place = like.getPlace();

        return new UserResDTO.LikedResponse(
                place.getId(),
                LikeCategory.PLACE,
                place.getName(),
                place.getThumbnailKey(),
                null,
                null,
                null,
                place.getRegion().getParent().getName(),
                List.of(),
                like.getCreatedAt().toString()
        );
    }

}
