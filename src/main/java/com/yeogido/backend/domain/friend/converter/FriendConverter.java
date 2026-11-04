package com.yeogido.backend.domain.friend.converter;

import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.enums.RelationStatus;
import com.yeogido.backend.domain.user.entity.User;
import java.util.List;

public class FriendConverter {

    private FriendConverter() {
    }

    public static FriendResDTO.MyFriendCode toMyFriendCode(User user) {
        return new FriendResDTO.MyFriendCode(user.getFriendCode());
    }

    public static FriendResDTO.UserSummary toUserSummary(User user, String profileImageUrl) {
        return new FriendResDTO.UserSummary(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                user.getFriendCode()
        );
    }

    public static FriendResDTO.FriendProfile toFriendProfile(User user, String profileImageUrl) {
        return new FriendResDTO.FriendProfile(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                user.getEmail(),
                new FriendResDTO.FriendRegion(user.getRegion().getId(), user.getRegion().getName())
        );
    }

    public static CourseResDTO.CoursePreview toCoursePreview(
            Course course,
            String thumbnailUrl,
            String routeImageUrl,
            List<String> tags,
            boolean isLiked,
            boolean canManage
    ) {
        return new CourseResDTO.CoursePreview(
                course.getId(),
                thumbnailUrl,
                routeImageUrl,
                course.getTitle(),
                course.getRegion().getName(),
                course.getDurationType(),
                course.getTransportType(),
                course.getCompanionType(),
                tags,
                isLiked,
                canManage
        );
    }

    public static FriendResDTO.SearchResult toSearchResult(
            User user,
            String profileImageUrl,
            RelationStatus relationStatus,
            Long requestId
    ) {
        return new FriendResDTO.SearchResult(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                user.getEmail(),
                relationStatus,
                requestId
        );
    }

    public static FriendResDTO.SentRequest toSentRequest(
            Friendship friendship,
            FriendResDTO.UserSummary receiver
    ) {
        return new FriendResDTO.SentRequest(
                friendship.getId(),
                receiver,
                friendship.getStatus(),
                friendship.getCreatedAt()
        );
    }

    public static FriendResDTO.ReceivedRequestItem toReceivedRequestItem(
            Friendship friendship,
            FriendResDTO.UserSummary requester
    ) {
        return new FriendResDTO.ReceivedRequestItem(
                friendship.getId(),
                requester,
                friendship.getCreatedAt()
        );
    }

    public static FriendResDTO.SentRequestItem toSentRequestItem(
            Friendship friendship,
            FriendResDTO.UserSummary receiver
    ) {
        return new FriendResDTO.SentRequestItem(
                friendship.getId(),
                receiver,
                friendship.getCreatedAt()
        );
    }

    public static FriendResDTO.AcceptResult toAcceptResult(
            Friendship friendship,
            FriendResDTO.UserSummary friend
    ) {
        return new FriendResDTO.AcceptResult(
                friendship.getId(),
                friendship.getStatus(),
                friend
        );
    }
}