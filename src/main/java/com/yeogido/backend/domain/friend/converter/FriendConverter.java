package com.yeogido.backend.domain.friend.converter;

import com.yeogido.backend.domain.friend.dto.FriendResDTO;
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
}
