package com.yeogido.backend.domain.friend.repository;

import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // 수락, 거절, 취소가 동시에 같은 요청을 처리하지 않도록 행을 잠그고 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select friendship from Friendship friendship where friendship.id = :id")
    Optional<Friendship> findByIdForUpdate(@Param("id") Long id);

    Optional<Friendship> findByUserLowIdAndUserHighId(Long userLowId, Long userHighId);

    // 회원 탈퇴 시 정리. 이 관계에 연결된 대기 초대는 FK CASCADE로 함께 삭제
    @Modifying(flushAutomatically = true)
    @Query("""
        delete from Friendship friendship
        where friendship.requester.id = :userId or friendship.receiver.id = :userId
        """)
    int deleteAllByUserId(@Param("userId") Long userId);

    // 공유앨범 자동 친구에서 행을 잠그지 않고 현재 관계만 확인
    @Query("""
        select friendship.status
        from Friendship friendship
        where friendship.userLowId = :userLowId and friendship.userHighId = :userHighId
        """)
    Optional<FriendshipStatus> findStatusBetween(
            @Param("userLowId") Long userLowId,
            @Param("userHighId") Long userHighId
    );

    // 공유앨범 자동 친구. 같은 두 사람의 행이 이미 있으면 오류 대신 0을 반환해 호출한 트랜잭션이 롤백되지 않도록 함
    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT IGNORE INTO friendship
            (requester_id, receiver_id, user_low_id, user_high_id, status, created_at, updated_at)
        VALUES (:requesterId, :receiverId, :userLowId, :userHighId, 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, nativeQuery = true)
    int insertAcceptedIgnore(
            @Param("requesterId") Long requesterId,
            @Param("receiverId") Long receiverId,
            @Param("userLowId") Long userLowId,
            @Param("userHighId") Long userHighId
    );

    // 대기 중인 요청만 수락. 호출한 쪽 영속성 컨텍스트를 비우지 않도록 clearAutomatically는 쓰지 않음
    @Modifying(flushAutomatically = true)
    @Query("""
        update Friendship friendship
        set friendship.status = com.yeogido.backend.domain.friend.enums.FriendshipStatus.ACCEPTED,
            friendship.updatedAt = CURRENT_TIMESTAMP
        where friendship.userLowId = :userLowId and friendship.userHighId = :userHighId
          and friendship.status = com.yeogido.backend.domain.friend.enums.FriendshipStatus.PENDING
        """)
    int acceptPendingBetween(@Param("userLowId") Long userLowId, @Param("userHighId") Long userHighId);

    boolean existsByUserLowIdAndUserHighIdAndStatus(
            Long userLowId,
            Long userHighId,
            FriendshipStatus status
    );

    // 검색 결과 행마다 조회하지 않도록 나와 대상들 사이의 관계를 한 번에 조회
    @Query("""
        select friendship
        from Friendship friendship
        where (friendship.requester.id = :userId and friendship.receiver.id in :otherUserIds)
           or (friendship.receiver.id = :userId and friendship.requester.id in :otherUserIds)
        """)
    List<Friendship> findAllBetween(
            @Param("userId") Long userId,
            @Param("otherUserIds") List<Long> otherUserIds
    );

    // 탈퇴·비활성 사용자가 보낸 요청은 제외
    @Query("""
        select friendship
        from Friendship friendship
        join fetch friendship.requester requester
        where friendship.receiver.id = :receiverId
          and friendship.status = com.yeogido.backend.domain.friend.enums.FriendshipStatus.PENDING
          and requester.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
          and (:cursorId is null or friendship.id < :cursorId)
        order by friendship.id desc
        """)
    List<Friendship> findPendingReceived(
            @Param("receiverId") Long receiverId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 탈퇴·비활성 사용자에게 보낸 요청은 제외
    @Query("""
        select friendship
        from Friendship friendship
        join fetch friendship.receiver receiver
        where friendship.requester.id = :requesterId
          and friendship.status = com.yeogido.backend.domain.friend.enums.FriendshipStatus.PENDING
          and receiver.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
          and (:cursorId is null or friendship.id < :cursorId)
        order by friendship.id desc
        """)
    List<Friendship> findPendingSent(
            @Param("requesterId") Long requesterId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 수락한 친구를 이름순으로 조회. 탈퇴·비활성 사용자는 제외하고, 같은 이름은 사용자 ID 순으로 구분
    @Query("""
        select friend
        from Friendship friendship
        join User friend
          on (friendship.requester.id = :userId and friend.id = friendship.receiver.id)
          or (friendship.receiver.id = :userId and friend.id = friendship.requester.id)
        where friendship.status = com.yeogido.backend.domain.friend.enums.FriendshipStatus.ACCEPTED
          and friend.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
          and (:namePattern is null
               or lower(friend.nickname) like :namePattern escape '!'
               or friend.friendCode like :codePattern escape '!')
          and (:cursorNickname is null
               or friend.nickname > :cursorNickname
               or (friend.nickname = :cursorNickname and friend.id > :cursorId))
        order by friend.nickname asc, friend.id asc
        """)
    List<User> findFriends(
            @Param("userId") Long userId,
            @Param("namePattern") String namePattern,
            @Param("codePattern") String codePattern,
            @Param("cursorNickname") String cursorNickname,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}