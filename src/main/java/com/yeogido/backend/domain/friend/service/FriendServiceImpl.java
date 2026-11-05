package com.yeogido.backend.domain.friend.service;

import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.repository.CourseHashtagRepository;
import com.yeogido.backend.domain.course.repository.CourseLikeRepository;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.converter.FriendConverter;
import com.yeogido.backend.domain.friend.dto.FriendReqDTO;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.friend.enums.RelationStatus;
import com.yeogido.backend.domain.friend.exception.FriendErrorCode;
import com.yeogido.backend.domain.friend.repository.FriendCourseRepository;
import com.yeogido.backend.domain.friend.repository.FriendshipRepository;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.repository.TravelRecordMemberRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordRepository;
import com.yeogido.backend.domain.travel.service.TravelRecordService;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendCourseRepository friendCourseRepository;
    private final CourseHashtagRepository courseHashtagRepository;
    private final CourseLikeRepository courseLikeRepository;
    private final TravelRecordService travelRecordService;
    private final S3Service s3Service;
    private final TravelRecordRepository travelRecordRepository;
    private final TravelRecordMemberRepository travelRecordMemberRepository;

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

        // 탈퇴·비활성 사용자가 보낸 요청은 목록에서처럼 없는 요청으로 처리
        if (friendship.getRequester().getStatus() != UserStatus.ACTIVE) {
            throw new GeneralException(FriendErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

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
    public CursorResponse<FriendResDTO.UserSummary> getFriends(
            Long userId,
            String keyword,
            String cursorNickname,
            Long cursorId,
            Integer size
    ) {
        validateSize(size);
        if ((cursorNickname == null) != (cursorId == null)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }

        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        String namePattern = null;
        String codePattern = null;
        if (!trimmedKeyword.isEmpty()) {
            String escapedKeyword = escapeLike(trimmedKeyword);
            namePattern = "%" + escapedKeyword.toLowerCase(Locale.ROOT) + "%";
            codePattern = escapedKeyword.toUpperCase(Locale.ROOT) + "%";
        }

        List<User> friends = friendshipRepository.findFriends(
                userId,
                namePattern,
                codePattern,
                cursorNickname,
                cursorId,
                PageRequest.of(0, size + 1)
        );

        boolean hasNext = friends.size() > size;
        List<User> pageItems = hasNext ? friends.subList(0, size) : friends;
        User last = hasNext ? pageItems.get(pageItems.size() - 1) : null;

        return CursorResponse.of(
                pageItems.stream().map(this::toUserSummary).toList(),
                last == null ? null : last.getNickname(),
                last == null ? null : last.getId(),
                hasNext
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FriendResDTO.FriendProfile getFriendProfile(Long userId, Long friendUserId) {
        User friend = getFriend(userId, friendUserId);

        return FriendConverter.toFriendProfile(friend, s3Service.getImageUrl(friend.getProfileImage()));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<CourseResDTO.CoursePreview> getFriendLocalCourses(
            Long userId,
            Long friendUserId,
            Long cursorId,
            Integer size
    ) {
        validateSize(size);
        getFriend(userId, friendUserId);
        // 작성자는 친구라 본인일 수 없으므로 관리자만 코스를 관리할 수 있음
        boolean canManage = getUser(userId).getRole() == UserRole.ADMIN;

        List<Course> courses = friendCourseRepository.findLocalCourses(
                friendUserId,
                cursorId,
                PageRequest.of(0, size + 1)
        );

        boolean hasNext = courses.size() > size;
        List<Course> pageItems = hasNext ? courses.subList(0, size) : courses;
        List<Long> courseIds = pageItems.stream().map(Course::getId).toList();
        Map<Long, List<String>> tagsByCourseId = getCourseTags(courseIds);
        Set<Long> likedCourseIds = getLikedCourseIds(userId, courseIds);

        List<CourseResDTO.CoursePreview> items = pageItems.stream()
                .map(course -> FriendConverter.toCoursePreview(
                        course,
                        s3Service.getImageUrl(course.getThumbnailKey()),
                        s3Service.getImageUrl(course.getRouteImageKey()),
                        tagsByCourseId.getOrDefault(course.getId(), List.of()),
                        likedCourseIds.contains(course.getId()),
                        canManage
                ))
                .toList();

        return CursorResponse.of(
                items,
                null,
                hasNext ? courseIds.get(courseIds.size() - 1) : null,
                hasNext
        );
    }

    /**
     * 친구 관계와 함께한 공유 앨범을 한 트랜잭션에서 정리합니다. 대기 초대는 친구 관계를 지울 때 FK CASCADE로 함께 삭제됩니다.
     * 초대 수락과 같은 순서(앨범 → 친구 관계)로 잠그고, 잠금 대기 중 커밋된 참여 변경을 읽도록 READ_COMMITTED를 사용합니다.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteFriend(Long userId, Long friendUserId) {
        getFriend(userId, friendUserId);

        lockSharedAlbums(userId, friendUserId);
        Friendship friendship = findFriendship(userId, friendUserId)
                .flatMap(found -> friendshipRepository.findByIdForUpdate(found.getId()))
                .orElseThrow(() -> new GeneralException(FriendErrorCode.NOT_FRIEND));

        // 친구 관계 잠금을 기다리는 사이 수락된 초대의 앨범까지 잠근 뒤 최신 참여자 수로 판단
        lockSharedAlbums(userId, friendUserId);
        travelRecordMemberRepository.findSharedAlbums(userId, friendUserId)
                .forEach(album -> cleanUpSharedAlbum(album, userId, friendUserId));

        friendshipRepository.delete(friendship);
        friendshipRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<TravelRecordResDTO.MapItem> getFriendTravelMap(
            Long userId,
            Long friendUserId,
            FriendReqDTO.TravelMapRequest request
    ) {
        getFriend(userId, friendUserId);

        // 공개범위 필터를 받지 않아 비공개 기록의 존재를 추측할 수 없도록 함
        return travelRecordService.getTravelMap(userId, friendUserId, new TravelRecordReqDTO.MapRequest(
                request.cursor(),
                request.size(),
                request.year(),
                null
        ));
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

    private void lockSharedAlbums(Long userId, Long friendUserId) {
        travelRecordMemberRepository.findSharedAlbums(userId, friendUserId)
                .forEach(album -> travelRecordRepository.findActiveDetailForUpdate(album.getTravelRecordId()));
    }

    private void cleanUpSharedAlbum(
            TravelRecordMemberRepository.SharedAlbum album,
            Long userId,
            Long friendUserId
    ) {
        Long travelRecordId = album.getTravelRecordId();
        if (!album.getOwnerId().equals(userId)) {
            travelRecordService.leaveTravelRecord(travelRecordId, userId);
        } else if (album.getRemainingMemberCount() == 0) {
            travelRecordService.deleteTravelRecord(travelRecordId, userId);
        } else {
            travelRecordService.excludeMemberByOwner(travelRecordId, userId, friendUserId);
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
    }

    // 친구가 아니거나 탈퇴·비활성 사용자면 친구가 아닌 것으로 처리
    private User getFriend(Long userId, Long friendUserId) {
        if (!areFriends(userId, friendUserId)) {
            throw new GeneralException(FriendErrorCode.NOT_FRIEND);
        }

        return userRepository.findById(friendUserId)
                .filter(friend -> friend.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(FriendErrorCode.NOT_FRIEND));
    }

    private Map<Long, List<String>> getCourseTags(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }

        return courseHashtagRepository.findHashtagNamesByCourseIdIn(courseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        CourseHashtagRepository.CourseHashtagNameProjection::getCourseId,
                        Collectors.mapping(
                                CourseHashtagRepository.CourseHashtagNameProjection::getHashtagName,
                                Collectors.toList()
                        )
                ));
    }

    private Set<Long> getLikedCourseIds(Long userId, List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(courseLikeRepository.findLikedCourseIdsByUserIdAndCourseIdIn(userId, courseIds));
    }

    // LIKE 특수문자를 일반 문자로 검색하도록 처리 (쿼리의 escape '!'와 짝)
    private String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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