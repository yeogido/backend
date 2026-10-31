package com.yeogido.backend.domain.friend.service;

import com.yeogido.backend.domain.friend.dto.FriendResDTO;

public interface FriendService {

    FriendResDTO.MyFriendCode getMyFriendCode(Long userId);
}
