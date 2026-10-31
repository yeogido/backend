package com.yeogido.backend.domain.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.dto.FriendReqDTO;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.friend.enums.RelationStatus;
import com.yeogido.backend.domain.friend.exception.FriendErrorCode;
import com.yeogido.backend.domain.friend.repository.FriendshipRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.ErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplTest {

    private static final Long ME_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long THIRD_ID = 3L;
    private static final Long REQUEST_ID = 10L;
    private static final String OTHER_CODE = "K7M2QX9A";

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private FriendServiceImpl friendService;

    private final User me = user(ME_ID, "AAAA2222");
    private final User other = user(OTHER_ID, OTHER_CODE);

    @Test
    void getMyFriendCodeReturnsCodeAndQrDeepLink() {
        when(userRepository.findById(ME_ID)).thenReturn(Optional.of(user(ME_ID, "K7M2QX9A")));

        FriendResDTO.MyFriendCode result = friendService.getMyFriendCode(ME_ID);

        assertThat(result.friendCode()).isEqualTo("K7M2QX9A");
        assertThat(result.qrValue()).isEqualTo("yeogido://friend?code=K7M2QX9A");
    }

    @Test
    void getMyFriendCodeFailsWhenUserNotFound() {
        when(userRepository.findById(ME_ID)).thenReturn(Optional.empty());

        assertErrorCode(() -> friendService.getMyFriendCode(ME_ID), UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void searchNormalizesCodeAndReturnsNoneWithoutRelation() {
        givenOtherFoundByCode();
        when(friendshipRepository.findByUserLowIdAndUserHighId(ME_ID, OTHER_ID)).thenReturn(Optional.empty());

        FriendResDTO.SearchResult result = friendService.searchByFriendCode(ME_ID, "  k7m2qx9a ");

        assertThat(result.userId()).isEqualTo(OTHER_ID);
        assertThat(result.email()).isEqualTo(other.getEmail());
        assertThat(result.relationStatus()).isEqualTo(RelationStatus.NONE);
        assertThat(result.requestId()).isNull();
    }

    @Test
    void searchReturnsRelationStatusAndRequestIdByFriendship() {
        givenOtherFoundByCode();

        when(friendshipRepository.findByUserLowIdAndUserHighId(ME_ID, OTHER_ID))
                .thenReturn(Optional.of(friendship(me, other, FriendshipStatus.PENDING)));
        FriendResDTO.SearchResult requested = friendService.searchByFriendCode(ME_ID, OTHER_CODE);
        assertThat(requested.relationStatus()).isEqualTo(RelationStatus.REQUESTED);
        assertThat(requested.requestId()).isEqualTo(REQUEST_ID);

        when(friendshipRepository.findByUserLowIdAndUserHighId(ME_ID, OTHER_ID))
                .thenReturn(Optional.of(friendship(other, me, FriendshipStatus.PENDING)));
        FriendResDTO.SearchResult received = friendService.searchByFriendCode(ME_ID, OTHER_CODE);
        assertThat(received.relationStatus()).isEqualTo(RelationStatus.RECEIVED);
        assertThat(received.requestId()).isEqualTo(REQUEST_ID);

        when(friendshipRepository.findByUserLowIdAndUserHighId(ME_ID, OTHER_ID))
                .thenReturn(Optional.of(friendship(other, me, FriendshipStatus.ACCEPTED)));
        FriendResDTO.SearchResult friend = friendService.searchByFriendCode(ME_ID, OTHER_CODE);
        assertThat(friend.relationStatus()).isEqualTo(RelationStatus.FRIEND);
        assertThat(friend.requestId()).isNull();
    }

    @Test
    void searchFailsWithOwnCode() {
        when(userRepository.findByFriendCodeAndStatus("AAAA2222", UserStatus.ACTIVE)).thenReturn(Optional.of(me));

        assertErrorCode(() -> friendService.searchByFriendCode(ME_ID, "AAAA2222"),
                FriendErrorCode.SELF_FRIEND_REQUEST);
    }

    @Test
    void searchFailsWhenCodeNotFoundOrUserInactive() {
        when(userRepository.findByFriendCodeAndStatus(OTHER_CODE, UserStatus.ACTIVE)).thenReturn(Optional.empty());

        assertErrorCode(() -> friendService.searchByFriendCode(ME_ID, OTHER_CODE),
                FriendErrorCode.FRIEND_CODE_NOT_FOUND);
    }

    @Test
    void sendRequestSavesPendingFriendship() {
        when(userRepository.findById(ME_ID)).thenReturn(Optional.of(me));
        givenOtherFoundByCode();
        when(friendshipRepository.findByUserLowIdAndUserHighId(ME_ID, OTHER_ID)).thenReturn(Optional.empty());
        when(friendshipRepository.saveAndFlush(any(Friendship.class))).thenAnswer(invocation -> {
            Friendship saved = invocation.getArgument(0);
            assertThat(saved.getUserLowId()).isEqualTo(ME_ID);
            assertThat(saved.getUserHighId()).isEqualTo(OTHER_ID);
            return friendship(saved.getRequester(), saved.getReceiver(), saved.getStatus());
        });

        FriendResDTO.SentRequest result = friendService.sendRequest(ME_ID, new FriendReqDTO.SendRequest(OTHER_CODE));

        assertThat(result.requestId()).isEqualTo(REQUEST_ID);
        assertThat(result.status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(result.receiver().userId()).isEqualTo(OTHER_ID);
        assertThat(result.receiver().friendCode()).isEqualTo(OTHER_CODE);
    }

    @Test
    void sendRequestFailsByExistingFriendship() {
        when(userRepository.findById(ME_ID)).thenReturn(Optional.of(me));
        givenOtherFoundByCode();

        when(friendshipRepository.findByUserLowIdAndUserHighId(ME_ID, OTHER_ID))
                .thenReturn(Optional.of(friendship(me, other, FriendshipStatus.ACCEPTED)));
        assertErrorCode(() -> friendService.sendRequest(ME_ID, new FriendReqDTO.SendRequest(OTHER_CODE)),
                FriendErrorCode.ALREADY_FRIEND);

        when(friendshipRepository.findByUserLowIdAndUserHighId(ME_ID, OTHER_ID))
                .thenReturn(Optional.of(friendship(me, other, FriendshipStatus.PENDING)));
        assertErrorCode(() -> friendService.sendRequest(ME_ID, new FriendReqDTO.SendRequest(OTHER_CODE)),
                FriendErrorCode.FRIEND_REQUEST_ALREADY_SENT);

        when(friendshipRepository.findByUserLowIdAndUserHighId(ME_ID, OTHER_ID))
                .thenReturn(Optional.of(friendship(other, me, FriendshipStatus.PENDING)));
        assertErrorCode(() -> friendService.sendRequest(ME_ID, new FriendReqDTO.SendRequest(OTHER_CODE)),
                FriendErrorCode.FRIEND_REQUEST_ALREADY_RECEIVED);

        verify(friendshipRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendRequestMapsConcurrentInsertToAlreadySent() {
        when(userRepository.findById(ME_ID)).thenReturn(Optional.of(me));
        givenOtherFoundByCode();
        when(friendshipRepository.findByUserLowIdAndUserHighId(ME_ID, OTHER_ID)).thenReturn(Optional.empty());
        when(friendshipRepository.saveAndFlush(any(Friendship.class)))
                .thenThrow(new DataIntegrityViolationException("uk_friendship_pair"));

        assertErrorCode(() -> friendService.sendRequest(ME_ID, new FriendReqDTO.SendRequest(OTHER_CODE)),
                FriendErrorCode.FRIEND_REQUEST_ALREADY_SENT);
    }

    @Test
    void getReceivedRequestsReturnsNextCursorWhenMoreItemsExist() {
        User third = user(THIRD_ID, "BBBB3333");
        when(friendshipRepository.findPendingReceived(eq(ME_ID), eq(null), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(
                        friendship(12L, other, me, FriendshipStatus.PENDING),
                        friendship(11L, third, me, FriendshipStatus.PENDING)
                ));

        CursorResponse<FriendResDTO.ReceivedRequestItem> result = friendService.getReceivedRequests(ME_ID, null, 1);

        assertThat(result.getItems()).extracting(FriendResDTO.ReceivedRequestItem::requestId).containsExactly(12L);
        assertThat(result.getItems().get(0).requester().userId()).isEqualTo(OTHER_ID);
        assertThat(result.isHasNext()).isTrue();
        assertThat(result.getCursorId()).isEqualTo(12L);
    }

    @Test
    void getSentRequestsReturnsLastPageWithoutCursor() {
        when(friendshipRepository.findPendingSent(eq(ME_ID), eq(20L), eq(PageRequest.of(0, 11))))
                .thenReturn(List.of(friendship(12L, me, other, FriendshipStatus.PENDING)));

        CursorResponse<FriendResDTO.SentRequestItem> result = friendService.getSentRequests(ME_ID, 20L, 10);

        assertThat(result.getItems()).extracting(item -> item.receiver().userId()).containsExactly(OTHER_ID);
        assertThat(result.isHasNext()).isFalse();
        assertThat(result.getCursorId()).isNull();
    }

    @Test
    void requestListsRejectInvalidSize() {
        assertErrorCode(() -> friendService.getReceivedRequests(ME_ID, null, 0), GeneralErrorCode.INVALID_PARAMETER);
        assertErrorCode(() -> friendService.getSentRequests(ME_ID, null, -1), GeneralErrorCode.INVALID_PARAMETER);
        assertErrorCode(() -> friendService.getReceivedRequests(ME_ID, null, 101), GeneralErrorCode.INVALID_PARAMETER);
        assertErrorCode(() -> friendService.getSentRequests(ME_ID, null, 101), GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    void requestListsAllowMaxSize() {
        when(friendshipRepository.findPendingReceived(eq(ME_ID), eq(null), eq(PageRequest.of(0, 101))))
                .thenReturn(List.of());

        assertThat(friendService.getReceivedRequests(ME_ID, null, 100).getItems()).isEmpty();
    }

    @Test
    void acceptRequestMakesFriends() {
        Friendship friendship = friendship(other, me, FriendshipStatus.PENDING);
        when(friendshipRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(friendship));

        FriendResDTO.AcceptResult result = friendService.acceptRequest(ME_ID, REQUEST_ID);

        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(result.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(result.friend().userId()).isEqualTo(OTHER_ID);
    }

    @Test
    void acceptAndRejectAllowOnlyPendingRequestForReceiver() {
        when(friendshipRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(friendship(me, other, FriendshipStatus.PENDING)));
        assertErrorCode(() -> friendService.acceptRequest(ME_ID, REQUEST_ID),
                FriendErrorCode.FRIEND_REQUEST_RESPOND_FORBIDDEN);
        assertErrorCode(() -> friendService.rejectRequest(ME_ID, REQUEST_ID),
                FriendErrorCode.FRIEND_REQUEST_RESPOND_FORBIDDEN);

        when(friendshipRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(friendship(other, me, FriendshipStatus.ACCEPTED)));
        assertErrorCode(() -> friendService.acceptRequest(ME_ID, REQUEST_ID),
                FriendErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
        assertErrorCode(() -> friendService.rejectRequest(ME_ID, REQUEST_ID),
                FriendErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);

        when(friendshipRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.empty());
        assertErrorCode(() -> friendService.acceptRequest(ME_ID, REQUEST_ID),
                FriendErrorCode.FRIEND_REQUEST_NOT_FOUND);

        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void rejectRequestDeletesFriendship() {
        Friendship friendship = friendship(other, me, FriendshipStatus.PENDING);
        when(friendshipRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(friendship));

        friendService.rejectRequest(ME_ID, REQUEST_ID);

        verify(friendshipRepository).delete(friendship);
    }

    @Test
    void cancelRequestDeletesOnlyOwnPendingRequest() {
        Friendship mine = friendship(me, other, FriendshipStatus.PENDING);
        when(friendshipRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(mine));
        friendService.cancelRequest(ME_ID, REQUEST_ID);
        verify(friendshipRepository).delete(mine);

        when(friendshipRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(friendship(other, me, FriendshipStatus.PENDING)));
        assertErrorCode(() -> friendService.cancelRequest(ME_ID, REQUEST_ID),
                FriendErrorCode.FRIEND_REQUEST_CANCEL_FORBIDDEN);

        when(friendshipRepository.findByIdForUpdate(REQUEST_ID))
                .thenReturn(Optional.of(friendship(me, other, FriendshipStatus.ACCEPTED)));
        assertErrorCode(() -> friendService.cancelRequest(ME_ID, REQUEST_ID),
                FriendErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
    }

    @Test
    void areFriendsChecksAcceptedPairInEitherOrder() {
        when(friendshipRepository.existsByUserLowIdAndUserHighIdAndStatus(ME_ID, OTHER_ID, FriendshipStatus.ACCEPTED))
                .thenReturn(true);

        assertThat(friendService.areFriends(OTHER_ID, ME_ID)).isTrue();
        assertThat(friendService.areFriends(ME_ID, OTHER_ID)).isTrue();
        assertThat(friendService.areFriends(ME_ID, ME_ID)).isFalse();
        assertThat(friendService.areFriends(ME_ID, null)).isFalse();
    }

    private void givenOtherFoundByCode() {
        when(userRepository.findByFriendCodeAndStatus(OTHER_CODE, UserStatus.ACTIVE)).thenReturn(Optional.of(other));
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    private static User user(Long id, String friendCode) {
        return User.builder()
                .id(id)
                .nickname("user" + id)
                .email("user" + id + "@test.com")
                .friendCode(friendCode)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private static Friendship friendship(User requester, User receiver, FriendshipStatus status) {
        return friendship(REQUEST_ID, requester, receiver, status);
    }

    private static Friendship friendship(Long id, User requester, User receiver, FriendshipStatus status) {
        return Friendship.builder()
                .id(id)
                .requester(requester)
                .receiver(receiver)
                .userLowId(Math.min(requester.getId(), receiver.getId()))
                .userHighId(Math.max(requester.getId(), receiver.getId()))
                .status(status)
                .build();
    }
}