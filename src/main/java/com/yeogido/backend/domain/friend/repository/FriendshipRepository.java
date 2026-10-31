package com.yeogido.backend.domain.friend.repository;

import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // 수락, 거절, 취소가 동시에 같은 요청을 처리하지 않도록 행을 잠그고 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select friendship from Friendship friendship where friendship.id = :id")
    Optional<Friendship> findByIdForUpdate(@Param("id") Long id);

    Optional<Friendship> findByUserLowIdAndUserHighId(Long userLowId, Long userHighId);

    boolean existsByUserLowIdAndUserHighIdAndStatus(
            Long userLowId,
            Long userHighId,
            FriendshipStatus status
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
}