package com.yeogido.backend.domain.content.dto;

import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public class ContentResDTO {

    public record ContentInfo(
            Long contentId,
            Long placeId,
            String title,
            String thumbnailImageUrl,
            String regionName,
            Long likeCount,
            LocalDate startDate,
            LocalDate endDate
    ) {}


    public record ContentDetailRes(
            Long contentId,
            String title,
            String description,
            String thumbnailImage,
            List<String> hashtags,
            LocalDate startDate,
            LocalDate endDate,
            Long likeCount,
            Boolean liked,
            PlaceInfo place,
            List<CourseInfo> courses
    ){}

    public record PlaceInfo(
            Long placeId,
            String name,
            String roadAddress,
            Double latitude,
            String phone,
            String officalUrl
    ){}

    public record CourseInfo(
            Long courseId,
            String title,
            String thumbnailImage,
            String description,
            DurationType durationType,
            TransportType transportType,
            Boolean liked

    ){}


    public record ContentCreateRes(
            Long contentId
    ){}

    public record ContentUpdateRes(
            Long contentId
    ){}

    public record ContentLikeRes(
            Boolean isLiked,
            Long likeCount
    ){}
}