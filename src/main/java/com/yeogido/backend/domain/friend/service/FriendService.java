package com.yeogido.backend.domain.friend.service;

import com.yeogido.backend.domain.friend.dto.FriendReqDTO;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

public interface FriendService {

    FriendResDTO.MyFriendCode getMyFriendCode(Long userId);

    FriendResDTO.SearchResult searchByFriendCode(Long userId, String friendCode);

    FriendResDTO.SentRequest sendRequest(Long userId, FriendReqDTO.SendRequest request);

    CursorResponse<FriendResDTO.ReceivedRequestItem> getReceivedRequests(Long userId, Long cursorId, Integer size);

    CursorResponse<FriendResDTO.SentRequestItem> getSentRequests(Long userId, Long cursorId, Integer size);

    FriendResDTO.AcceptResult acceptRequest(Long userId, Long requestId);

    void rejectRequest(Long userId, Long requestId);

    void cancelRequest(Long userId, Long requestId);

    // 다른 도메인(공유 앨범 초대 등)에서 친구 관계를 확인할 때 사용
    boolean areFriends(Long userIdA, Long userIdB);
}