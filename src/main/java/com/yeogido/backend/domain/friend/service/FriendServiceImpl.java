package com.yeogido.backend.domain.friend.service;

import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.converter.FriendConverter;
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
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final S3Service s3Service;

    @Override
    @Transactional(readOnly = true)
    public FriendResDTO.MyFriendCode getMyFriendCode(Long userId) {
        User user = getUser(userId);

        return FriendConverter.toMyFriendCode(user);
    }

    @Override
    @Transactional(readOnly = true)
    public FriendResDTO.SearchResult searchByFriendCode(Long userId, String friendCode) {
        User target = getTargetByFriendCode(userId, friendCode);

        Optional<Friendship> friendship = findFriendship(userId, target.getId());
        RelationStatus relationStatus = friendship
                .map(found -> toRelationStatus(found, userId))
                .orElse(RelationStatus.NONE);
        Long requestId = friendship
                .filter(Friendship::isPending)
                .map(Friendship::getId)
                .orElse(null);

        return FriendConverter.toSearchResult(
                target,
                s3Service.getImageUrl(target.getProfileImage()),
                relationStatus,
                requestId
        );
    }

    @Override
    @Transactional
    public FriendResDTO.SentRequest sendRequest(Long userId, FriendReqDTO.SendRequest request) {
        User requester = getUser(userId);
        User receiver = getTargetByFriendCode(userId, request.friendCode());

        findFriendship(userId, receiver.getId())
                .ifPresent(friendship -> {
                    throw new GeneralException(toDuplicateRequestError(friendship, userId));
                });

        Friendship friendship;
        try {
            friendship = friendshipRepository.saveAndFlush(Friendship.request(requester, receiver));
        } catch (DataIntegrityViolationException e) {
            // 같은 두 사람의 요청이 동시에 저장된 경우 (대부분 중복 클릭)
            throw new GeneralException(FriendErrorCode.FRIEND_REQUEST_ALREADY_SENT);
        }

        return FriendConverter.toSentRequest(friendship, toUserSummary(receiver));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<FriendResDTO.ReceivedRequestItem> getReceivedRequests(
            Long userId,
            Long cursorId,
            Integer size
    ) {
        validateSize(size);

        List<Friendship> friendships = friendshipRepository.findPendingReceived(
                userId,
                cursorId,
                PageRequest.of(0, size + 1)
        );

        boolean hasNext = friendships.size() > size;
        List<Friendship> pageItems = hasNext ? friendships.subList(0, size) : friendships;

        List<FriendResDTO.ReceivedRequestItem> items = pageItems.stream()
                .map(friendship -> FriendConverter.toReceivedRequestItem(
                        friendship,
                        toUserSummary(friendship.getRequester())
                ))
                .toList();

        return CursorResponse.of(items, null, nextCursorId(pageItems, hasNext), hasNext);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<FriendResDTO.SentRequestItem> getSentRequests(
            Long userId,
            Long cursorId,
            Integer size
    ) {
        validateSize(size);

        List<Friendship> friendships = friendshipRepository.findPendingSent(
                userId,
                cursorId,
                PageRequest.of(0, size + 1)
        );

        boolean hasNext = friendships.size() > size;
        List<Friendship> pageItems = hasNext ? friendships.subList(0, size) : friendships;

        List<FriendResDTO.SentRequestItem> items = pageItems.stream()
                .map(friendship -> FriendConverter.toSentRequestItem(
                        friendship,
                        toUserSummary(friendship.getReceiver())
                ))
                .toList();

        return CursorResponse.of(items, null, nextCursorId(pageItems, hasNext), hasNext);
    }

    @Override
    @Transactional
    public FriendResDTO.AcceptResult acceptRequest(Long userId, Long requestId) {
        Friendship friendship = getPendingRequestForReceiver(userId, requestId);

        friendship.accept();

        return FriendConverter.toAcceptResult(friendship, toUserSummary(friendship.getRequester()));
    }

    @Override
    @Transactional
    public void rejectRequest(Long userId, Long requestId) {
        Friendship friendship = getPendingRequestForReceiver(userId, requestId);

        friendshipRepository.delete(friendship);
    }

    @Override
    @Transactional
    public void cancelRequest(Long userId, Long requestId) {
        Friendship friendship = getFriendship(requestId);

        if (!friendship.isRequester(userId)) {
            throw new GeneralException(FriendErrorCode.FRIEND_REQUEST_CANCEL_FORBIDDEN);
        }
        if (!friendship.isPending()) {
            throw new GeneralException(FriendErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
        }

        friendshipRepository.delete(friendship);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean areFriends(Long userIdA, Long userIdB) {
        if (userIdA == null || userIdB == null || userIdA.equals(userIdB)) {
            return false;
        }

        return friendshipRepository.existsByUserLowIdAndUserHighIdAndStatus(
                Math.min(userIdA, userIdB),
                Math.max(userIdA, userIdB),
                FriendshipStatus.ACCEPTED
        );
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
    }

    // 탈퇴·비활성 사용자의 코드는 존재하지 않는 코드로 처리
    private User getTargetByFriendCode(Long userId, String friendCode) {
        User target = userRepository.findByFriendCodeAndStatus(normalize(friendCode), UserStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(FriendErrorCode.FRIEND_CODE_NOT_FOUND));

        if (target.getId().equals(userId)) {
            throw new GeneralException(FriendErrorCode.SELF_FRIEND_REQUEST);
        }
        return target;
    }

    private String normalize(String friendCode) {
        return friendCode == null ? "" : friendCode.trim().toUpperCase(Locale.ROOT);
    }

    private Optional<Friendship> findFriendship(Long userIdA, Long userIdB) {
        return friendshipRepository.findByUserLowIdAndUserHighId(
                Math.min(userIdA, userIdB),
                Math.max(userIdA, userIdB)
        );
    }

    private Friendship getFriendship(Long requestId) {
        return friendshipRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new GeneralException(FriendErrorCode.FRIEND_REQUEST_NOT_FOUND));
    }

    private Friendship getPendingRequestForReceiver(Long userId, Long requestId) {
        Friendship friendship = getFriendship(requestId);

        if (!friendship.isReceiver(userId)) {
            throw new GeneralException(FriendErrorCode.FRIEND_REQUEST_RESPOND_FORBIDDEN);
        }
        if (!friendship.isPending()) {
            throw new GeneralException(FriendErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
        }
        return friendship;
    }

    private RelationStatus toRelationStatus(Friendship friendship, Long userId) {
        if (!friendship.isPending()) {
            return RelationStatus.FRIEND;
        }
        return friendship.isRequester(userId) ? RelationStatus.REQUESTED : RelationStatus.RECEIVED;
    }

    private FriendErrorCode toDuplicateRequestError(Friendship friendship, Long userId) {
        return switch (toRelationStatus(friendship, userId)) {
            case FRIEND -> FriendErrorCode.ALREADY_FRIEND;
            case REQUESTED -> FriendErrorCode.FRIEND_REQUEST_ALREADY_SENT;
            default -> FriendErrorCode.FRIEND_REQUEST_ALREADY_RECEIVED;
        };
    }

    private FriendResDTO.UserSummary toUserSummary(User user) {
        return FriendConverter.toUserSummary(user, s3Service.getImageUrl(user.getProfileImage()));
    }

    private void validateSize(Integer size) {
        if (size == null || size < 1 || size > MAX_PAGE_SIZE) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
    }

    private Long nextCursorId(List<Friendship> pageItems, boolean hasNext) {
        if (!hasNext || pageItems.isEmpty()) {
            return null;
        }
        return pageItems.get(pageItems.size() - 1).getId();
    }
}