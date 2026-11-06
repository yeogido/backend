package com.yeogido.backend.domain.friend.service;

import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.friend.dto.FriendReqDTO;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

public interface FriendService {

    FriendResDTO.MyFriendCode getMyFriendCode(Long userId);

    CursorResponse<FriendResDTO.SearchResult> searchUsers(
            Long userId,
            String keyword,
            Long cursorId,
            Integer size
    );

    FriendResDTO.QrUser getUserByQrToken(Long userId, String qrToken);

    FriendResDTO.SentRequest sendRequest(Long userId, FriendReqDTO.SendRequest request);

    CursorResponse<FriendResDTO.ReceivedRequestItem> getReceivedRequests(Long userId, Long cursorId, Integer size);

    CursorResponse<FriendResDTO.SentRequestItem> getSentRequests(Long userId, Long cursorId, Integer size);

    FriendResDTO.AcceptResult acceptRequest(Long userId, Long requestId);

    void rejectRequest(Long userId, Long requestId);

    void cancelRequest(Long userId, Long requestId);

    CursorResponse<FriendResDTO.UserSummary> getFriends(
            Long userId,
            String keyword,
            String cursorNickname,
            Long cursorId,
            Integer size
    );

    FriendResDTO.FriendProfile getFriendProfile(Long userId, Long friendUserId);

    CursorResponse<CourseResDTO.CoursePreview> getFriendLocalCourses(
            Long userId,
            Long friendUserId,
            Long cursorId,
            Integer size
    );

    void deleteFriend(Long userId, Long friendUserId);

    CursorResponse<TravelRecordResDTO.MapItem> getFriendTravelMap(
            Long userId,
            Long friendUserId,
            FriendReqDTO.TravelMapRequest request
    );

    // 다른 도메인(공유 앨범 초대 등)에서 친구 관계를 확인할 때 사용
    boolean areFriends(Long userIdA, Long userIdB);

    /**
     * 공유앨범 초대 수락 시 초대한 사람과 초대받은 사람을 친구로 만듭니다. 이미 친구면 변경하지 않고,
     * 대기 중인 요청이 있으면 수락하며, 관계가 없으면 초대한 사람을 요청자로 친구 관계를 만듭니다.
     * 호출한 쪽 트랜잭션에 합류하며 동시에 호출되어도 중복 저장 오류로 롤백되지 않습니다. 탈퇴·비활성 사용자면 변경하지 않습니다.
     */
    void befriendByAlbumInvitation(Long inviterId, Long inviteeId);
}