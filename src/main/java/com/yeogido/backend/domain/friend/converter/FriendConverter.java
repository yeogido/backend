package com.yeogido.backend.domain.friend.converter;

import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.enums.RelationStatus;
import com.yeogido.backend.domain.user.entity.User;

public class FriendConverter {

    private static final String QR_DEEP_LINK_PREFIX = "yeogido://friend?code=";

    private FriendConverter() {
    }

    public static FriendResDTO.MyFriendCode toMyFriendCode(User user) {
        return new FriendResDTO.MyFriendCode(
                user.getFriendCode(),
                QR_DEEP_LINK_PREFIX + user.getFriendCode()
        );
    }

    public static FriendResDTO.UserSummary toUserSummary(User user, String profileImageUrl) {
        return new FriendResDTO.UserSummary(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                user.getFriendCode()
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