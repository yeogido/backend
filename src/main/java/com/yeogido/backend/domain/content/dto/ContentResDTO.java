package com.yeogido.backend.domain.content.dto;

import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public class ContentResDTO {

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ContentInfo{
            private Long contentId;
            private Long placeId;
            private String title;
            private String thumbnailImageUrl;
            private String regionName;
            private Long likeCount;
            private LocalDate startDate;
            private LocalDate endDate;
    }


    @Getter
    @Builder
    @AllArgsConstructor
    public static class ContentDetailRes {
        private Long contentId;
        private String title;
        private String description;
        private String thumbnailImage;
        private List<String> hashtags;
        private LocalDate startDate;
        private LocalDate endDate;
        private Long likeCount;
        private Boolean liked;
        private PlaceInfo place;
        private List<CourseInfo> courses;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class PlaceInfo {
        private Long placeId;
        private String name;
        private String roadAddress;
        private Double latitude;
        private String phone;
        private String officalUrl;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CourseInfo {
        private Long courseId;
        private String title;
        private String thumbnailImage;
        private String description;
        private DurationType durationType;
        private TransportType transportType;
        private Boolean liked;

    }


    @Getter
    @Builder
    @AllArgsConstructor
    public static class ContentCreateRes {
        private Long contentId;
    }



    @Getter
    @Builder
    @AllArgsConstructor
    public static class ContentUpdateRes{
            private Long contentId;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ContentLikeRes{
            private Boolean isLiked;
            private Long likeCount;
    }
}