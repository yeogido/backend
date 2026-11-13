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
import java.util.function.Function;
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
    private final FriendQrTokenService friendQrTokenService;
    private final TravelRecordRepository travelRecordRepository;
    private final TravelRecordMemberRepository travelRecordMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public FriendResDTO.MyFriendCode getMyFriendCode(Long userId) {
        User user = getUser(userId);
        FriendQrTokenService.IssuedToken qrToken = friendQrTokenService.issue(userId);

        return FriendConverter.toMyFriendCode(user, qrToken.token(), qrToken.expiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<FriendResDTO.SearchResult> searchUsers(
            Long userId,
            String keyword,
            Long cursorId,
            Integer size
    ) {
        validateSize(size);
        if (keyword == null || keyword.isBlank()) {
            return CursorResponse.of(List.of(), null, null, false);
        }

        List<User> users = userRepository.findSearchableUsers(
                userId,
                keyword.trim(),
                normalize(keyword),
                cursorId,
                PageRequest.of(0, size + 1)
        );
        boolean hasNext = users.size() > size;
        List<User> pageItems = hasNext ? users.subList(0, size) : users;
        if (pageItems.isEmpty()) {
            return CursorResponse.of(List.of(), null, null, false);
        }

        Map<Long, Friendship> friendships = friendshipRepository
                .findAllBetween(userId, pageItems.stream().map(User::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        friendship -> friendship.isRequester(userId)
                                ? friendship.getReceiver().getId()
                                : friendship.getRequester().getId(),
                        Function.identity()
                ));

        List<FriendResDTO.SearchResult> items = pageItems.stream()
                .map(user -> {
                    Optional<Friendship> friendship = Optional.ofNullable(friendships.get(user.getId()));
                    return FriendConverter.toSearchResult(
                            user,
                            s3Service.getImageUrl(user.getProfileImage()),
                            toRelationStatus(friendship, userId),
                            toRequestId(friendship)
                    );
                })
                .toList();

        return CursorResponse.of(items, null, hasNext ? pageItems.getLast().getId() : null, hasNext);
    }

    @Override
    @Transactional(readOnly = true)
    public FriendResDTO.QrUser getUserByQrToken(Long userId, String qrToken) {
        Long targetId = friendQrTokenService.findUserId(qrToken)
                .orElseThrow(() -> new GeneralException(FriendErrorCode.FRIEND_QR_TOKEN_NOT_FOUND));
        if (targetId.equals(userId)) {
            throw new GeneralException(FriendErrorCode.SELF_FRIEND_REQUEST);
        }

        User target = userRepository.findById(targetId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(FriendErrorCode.FRIEND_QR_TOKEN_NOT_FOUND));
        Optional<Friendship> friendship = findFriendship(userId, targetId);

        return FriendConverter.toQrUser(
                target,
                s3Service.getImageUrl(target.getProfileImage()),
                toRelationStatus(friendship, userId),
                toRequestId(friendship)
        );
    }

    // 결과 행을 읽어 응답해야 하므로 READ_COMMITTED 사용. REPEATABLE READ면 첫 조회 시점 스냅샷에 묶여
    // 동시에 상대가 먼저 만든 행을 마지막 조회에서 보지 못함
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FriendResDTO.AcceptResult befriendByQrToken(Long userId, String qrToken) {
        Long targetId = friendQrTokenService.findUserId(qrToken)
                .orElseThrow(() -> new GeneralException(FriendErrorCode.FRIEND_QR_TOKEN_NOT_FOUND));
        if (targetId.equals(userId)) {
            throw new GeneralException(FriendErrorCode.SELF_FRIEND_REQUEST);
        }
        getUser(userId);
        User target = userRepository.findById(targetId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(FriendErrorCode.FRIEND_QR_TOKEN_NOT_FOUND));

        Long userLowId = Math.min(userId, targetId);
        Long userHighId = Math.max(userId, targetId);
        Optional<FriendshipStatus> status = friendshipRepository.findStatusBetween(userLowId, userHighId);
        if (status.filter(FriendshipStatus.ACCEPTED::equals).isPresent()) {
            throw new GeneralException(FriendErrorCode.ALREADY_FRIEND);
        }

        // QR을 찍은 사람이 요청자. 저장 직후 상대가 친구를 삭제한 경우만 다시 한 번 시도
        Friendship friendship = null;
        for (int attempt = 0; attempt < 2 && friendship == null; attempt++) {
            acceptPendingOrCreateAccepted(userId, targetId, status.isPresent());
            friendship = friendshipRepository.findByUserLowIdAndUserHighId(userLowId, userHighId)
                    .filter(found -> found.getStatus() == FriendshipStatus.ACCEPTED)
                    .orElse(null);
        }
        if (friendship == null) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
        }

        return FriendConverter.toAcceptResult(friendship, toUserSummary(target));
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
        User target = getTargetUser(userId, friendUserId);
        Optional<Friendship> friendship = findFriendship(userId, friendUserId);

        return FriendConverter.toFriendProfile(
                target,
                s3Service.getImageUrl(target.getProfileImage()),
                toRelationStatus(friendship, userId),
                toRequestId(friendship)
        );
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
        getTargetUser(userId, friendUserId);
        // 작성자는 본인이 아니므로 관리자만 코스를 관리할 수 있음
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

    /** 친구에게는 열람할 수 없는 기록도 블러 항목으로 내려주고, 친구가 아니면 열람할 수 있는 기록만 내려줍니다. */
    @Override
    @Transactional(readOnly = true)
    public CursorResponse<FriendResDTO.TravelMapItem> getFriendTravelMap(
            Long userId,
            Long friendUserId,
            FriendReqDTO.TravelMapRequest request
    ) {
        getTargetUser(userId, friendUserId);

        // 공개범위 필터를 받지 않아 비공개 기록의 존재를 추측할 수 없도록 함
        CursorResponse<TravelRecordResDTO.UserMapEntry> page = travelRecordService.getUserTravelMap(
                userId,
                friendUserId,
                areFriends(userId, friendUserId),
                request.cursor(),
                request.size(),
                request.year()
        );
        return CursorResponse.of(
                page.getItems().stream().map(FriendConverter::toTravelMapItem).toList(),
                page.getCursorValue(),
                page.getCursorId(),
                page.isHasNext()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TravelRecordResDTO.YearListResponse getFriendTravelMapYears(Long userId, Long friendUserId) {
        getTargetUser(userId, friendUserId);

        return new TravelRecordResDTO.YearListResponse(travelRecordService.getUserTravelMapYears(
                userId,
                friendUserId,
                areFriends(userId, friendUserId)
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

    // 없는 행을 잠금 조회하면 REPEATABLE READ에서 갭 잠금이 걸려 이어지는 insert끼리 교착될 수 있어
    // 잠금 없이 상태를 읽고, 저장은 INSERT IGNORE와 조건부 update로만 처리
    @Override
    @Transactional
    public void befriendByAlbumInvitation(Long inviterId, Long inviteeId) {
        if (inviterId == null || inviteeId == null || inviterId.equals(inviteeId)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
        if (!isActiveUser(inviterId) || !isActiveUser(inviteeId)) {
            return;
        }

        Optional<FriendshipStatus> status = friendshipRepository.findStatusBetween(
                Math.min(inviterId, inviteeId), Math.max(inviterId, inviteeId));
        if (status.filter(FriendshipStatus.ACCEPTED::equals).isPresent()) {
            return;
        }
        acceptPendingOrCreateAccepted(inviterId, inviteeId, status.isPresent());
    }

    // 공유앨범 자동 친구와 QR 즉시 친구가 함께 쓰는 저장 로직. 대기 요청이 있으면 방향을 유지한 채 수락하고,
    // 없으면 requesterId를 요청자로 ACCEPTED 행을 만듦
    private void acceptPendingOrCreateAccepted(Long requesterId, Long receiverId, boolean relationExists) {
        Long userLowId = Math.min(requesterId, receiverId);
        Long userHighId = Math.max(requesterId, receiverId);
        if (relationExists && friendshipRepository.acceptPendingBetween(userLowId, userHighId) > 0) {
            return;
        }

        // 관계가 없거나, 읽은 뒤 대기 요청이 거절·취소된 경우 새로 만듦
        // 그사이 다른 요청이 먼저 행을 만들었으면 그 행이 대기 중일 때만 수락
        if (friendshipRepository.insertAcceptedIgnore(requesterId, receiverId, userLowId, userHighId) == 0) {
            friendshipRepository.acceptPendingBetween(userLowId, userHighId);
        }
    }

    private boolean isActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .isPresent();
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

    // 친구 여부와 관계없이 프로필을 볼 수 있는 대상. 내 ID는 프로필 API 대상이 아님
    private User getTargetUser(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }

        return userRepository.findById(targetUserId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
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

    private RelationStatus toRelationStatus(Optional<Friendship> friendship, Long userId) {
        return friendship
                .map(found -> toRelationStatus(found, userId))
                .orElse(RelationStatus.NONE);
    }

    private Long toRequestId(Optional<Friendship> friendship) {
        return friendship
                .filter(Friendship::isPending)
                .map(Friendship::getId)
                .orElse(null);
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