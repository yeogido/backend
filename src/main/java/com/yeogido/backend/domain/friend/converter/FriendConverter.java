package com.yeogido.backend.domain.friend.converter;

import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.enums.RelationStatus;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;

public class FriendConverter {

    private FriendConverter() {
    }

    public static FriendResDTO.MyFriendCode toMyFriendCode(
            User user,
            String qrToken,
            LocalDateTime qrExpiresAt
    ) {
        return new FriendResDTO.MyFriendCode(user.getFriendCode(), qrToken, qrExpiresAt);
    }

    public static FriendResDTO.UserSummary toUserSummary(User user, String profileImageUrl) {
        return new FriendResDTO.UserSummary(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                user.getFriendCode()
        );
    }

    // 친구가 아니면 사용자 ID만으로 이메일을 알아낼 수 없도록 이메일을 비움
    public static FriendResDTO.FriendProfile toFriendProfile(
            User user,
            String profileImageUrl,
            RelationStatus relationStatus,
            Long requestId
    ) {
        return new FriendResDTO.FriendProfile(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                user.getFriendCode(),
                relationStatus == RelationStatus.FRIEND ? user.getEmail() : null,
                new FriendResDTO.FriendRegion(user.getRegion().getId(), user.getRegion().getName()),
                relationStatus,
                requestId
        );
    }

    public static FriendResDTO.TravelMapItem toTravelMapItem(TravelRecordResDTO.UserMapEntry entry) {
        TravelRecordResDTO.MapItem item = entry.mapItem();
        return new FriendResDTO.TravelMapItem(
                entry.blurred(),
                item.travelRecordId(),
                item.title(),
                item.regionId(),
                item.regionName(),
                item.latitude(),
                item.longitude(),
                item.startDate(),
                item.endDate(),
                item.visibility(),
                item.images()
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

    public static FriendResDTO.QrUser toQrUser(
            User user,
            String profileImageUrl,
            RelationStatus relationStatus,
            Long requestId
    ) {
        return new FriendResDTO.QrUser(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                user.getFriendCode(),
                relationStatus,
                requestId
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
                user.getFriendCode(),
                relationStatus == RelationStatus.FRIEND ? user.getEmail() : null,
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