package com.yeogido.backend.domain.friend.entity;

import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "friendship",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_friendship_pair",
                        columnNames = {"user_low_id", "user_high_id"}
                )
        }
)
public class Friendship extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    // 요청 방향과 관계없이 두 사람을 한 쌍으로 찾기 위한 작은 ID·큰 ID
    @Column(name = "user_low_id", nullable = false)
    private Long userLowId;

    @Column(name = "user_high_id", nullable = false)
    private Long userHighId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendshipStatus status;

    public static Friendship request(User requester, User receiver) {
        return Friendship.builder()
                .requester(requester)
                .receiver(receiver)
                .userLowId(Math.min(requester.getId(), receiver.getId()))
                .userHighId(Math.max(requester.getId(), receiver.getId()))
                .status(FriendshipStatus.PENDING)
                .build();
    }

    public void accept() {
        this.status = FriendshipStatus.ACCEPTED;
    }

    public boolean isPending() {
        return this.status == FriendshipStatus.PENDING;
    }

    public boolean isRequester(Long userId) {
        return this.requester.getId().equals(userId);
    }

    public boolean isReceiver(Long userId) {
        return this.receiver.getId().equals(userId);
    }
}