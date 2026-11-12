package com.yeogido.backend.domain.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.dto.FriendResDTO;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.friend.exception.FriendErrorCode;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 공유앨범 자동 친구를 실제 트랜잭션으로 확인합니다. 호출한 쪽 트랜잭션에 합류하고, 동시에 호출돼도 오류 없이 한 행만 남아야 합니다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:friend-auto-befriend;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class FriendAutoBefriendTransactionTest {

    @Autowired private FriendService friendService;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;
    @MockitoBean private FriendQrTokenService friendQrTokenService;

    // QR 즉시 친구는 같은 저장 로직을 쓰되 자체 트랜잭션에서 결과 행을 읽어 응답함
    @Test
    void qrBefriendCreatesAcceptedFriendshipWithScannerAsRequester() {
        Pair pair = pair();
        givenQrOwner("qr", pair.inviteeId());

        FriendResDTO.AcceptResult result = friendService.befriendByQrToken(pair.inviterId(), "qr");

        Friendship friendship = friendships(pair).getFirst();
        assertThat(friendships(pair)).hasSize(1);
        assertThat(result.requestId()).isEqualTo(friendship.getId());
        assertThat(result.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(result.friend().userId()).isEqualTo(pair.inviteeId());
        assertThat(friendship.getRequester().getId()).isEqualTo(pair.inviterId());
        assertThat(friendService.areFriends(pair.inviterId(), pair.inviteeId())).isTrue();
    }

    @Test
    void qrBefriendAcceptsPendingRequestAndRejectsWhenAlreadyFriend() {
        Pair pair = pair();
        Long requestId = persistFriendship(pair.inviteeId(), pair.inviterId(), false);
        givenQrOwner("qr", pair.inviteeId());

        FriendResDTO.AcceptResult result = friendService.befriendByQrToken(pair.inviterId(), "qr");

        assertThat(result.requestId()).isEqualTo(requestId);
        assertThat(friendships(pair)).extracting(Friendship::getStatus).containsExactly(FriendshipStatus.ACCEPTED);
        assertThatThrownBy(() -> friendService.befriendByQrToken(pair.inviterId(), "qr"))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FriendErrorCode.ALREADY_FRIEND));
    }

    @Test
    void concurrentQrBefriendFromBothSidesLeavesSingleFriendship() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                Pair pair = pair();
                givenQrOwner("inviter-qr", pair.inviterId());
                givenQrOwner("invitee-qr", pair.inviteeId());
                CyclicBarrier barrier = new CyclicBarrier(2);
                // 서로의 QR을 동시에 찍어도 한쪽은 성공하고 다른 쪽은 성공하거나 "이미 친구"만 받아야 함
                CompletableFuture<Void> first = CompletableFuture.runAsync(() -> {
                    await(barrier);
                    befriendIgnoringAlreadyFriend(pair.inviterId(), "invitee-qr");
                }, executor);
                CompletableFuture<Void> second = CompletableFuture.runAsync(() -> {
                    await(barrier);
                    befriendIgnoringAlreadyFriend(pair.inviteeId(), "inviter-qr");
                }, executor);

                CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);

                assertThat(friendships(pair)).hasSize(1)
                        .allSatisfy(friendship -> assertThat(friendship.getStatus())
                                .isEqualTo(FriendshipStatus.ACCEPTED));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void givenQrOwner(String token, Long ownerId) {
        org.mockito.Mockito.when(friendQrTokenService.findUserId(token)).thenReturn(Optional.of(ownerId));
    }

    private void befriendIgnoringAlreadyFriend(Long userId, String token) {
        try {
            friendService.befriendByQrToken(userId, token);
        } catch (GeneralException e) {
            if (e.getErrorCode() != FriendErrorCode.ALREADY_FRIEND) {
                throw e;
            }
        }
    }

    @Test
    void createsAcceptedFriendshipWithInviterAsRequester() {
        Pair pair = pair();

        inTransaction(() -> friendService.befriendByAlbumInvitation(pair.inviterId(), pair.inviteeId()));

        Friendship friendship = friendships(pair).getFirst();
        assertThat(friendships(pair)).hasSize(1);
        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(friendship.getRequester().getId()).isEqualTo(pair.inviterId());
        assertThat(friendship.getReceiver().getId()).isEqualTo(pair.inviteeId());
    }

    @Test
    void acceptsPendingRequestKeepingItsDirection() {
        Pair pair = pair();
        Long requestId = persistFriendship(pair.inviteeId(), pair.inviterId(), false);

        inTransaction(() -> friendService.befriendByAlbumInvitation(pair.inviterId(), pair.inviteeId()));

        Friendship friendship = friendships(pair).getFirst();
        assertThat(friendships(pair)).hasSize(1);
        assertThat(friendship.getId()).isEqualTo(requestId);
        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(friendship.getRequester().getId()).isEqualTo(pair.inviteeId());
    }

    @Test
    void keepsExistingFriendship() {
        Pair pair = pair();
        Long friendshipId = persistFriendship(pair.inviteeId(), pair.inviterId(), true);

        inTransaction(() -> friendService.befriendByAlbumInvitation(pair.inviterId(), pair.inviteeId()));

        assertThat(friendships(pair)).extracting(Friendship::getId).containsExactly(friendshipId);
        assertThat(friendships(pair).getFirst().getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    void rollsBackWithCallerTransaction() {
        Pair pair = pair();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            friendService.befriendByAlbumInvitation(pair.inviterId(), pair.inviteeId());
            assertThat(friendService.areFriends(pair.inviterId(), pair.inviteeId())).isTrue();
            status.setRollbackOnly();
        });

        assertThat(friendships(pair)).isEmpty();
    }

    @Test
    void ignoresWithdrawnUserAndRejectsSameOrMissingId() {
        Pair pair = pair();
        inTransaction(() -> entityManager.find(User.class, pair.inviteeId()).withdraw());

        inTransaction(() -> friendService.befriendByAlbumInvitation(pair.inviterId(), pair.inviteeId()));

        assertThat(friendships(pair)).isEmpty();
        assertThatThrownBy(() -> friendService.befriendByAlbumInvitation(pair.inviterId(), pair.inviterId()))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
        assertThatThrownBy(() -> friendService.befriendByAlbumInvitation(null, pair.inviteeId()))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void concurrentCallsLeaveSingleFriendshipWithoutError() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                Pair pair = pair();
                CyclicBarrier barrier = new CyclicBarrier(2);
                // 양쪽 방향으로 동시에 호출해도 호출한 트랜잭션이 롤백되지 않아야 함
                CompletableFuture<Void> first = CompletableFuture.runAsync(() -> inTransaction(() -> {
                    await(barrier);
                    friendService.befriendByAlbumInvitation(pair.inviterId(), pair.inviteeId());
                }), executor);
                CompletableFuture<Void> second = CompletableFuture.runAsync(() -> inTransaction(() -> {
                    await(barrier);
                    friendService.befriendByAlbumInvitation(pair.inviteeId(), pair.inviterId());
                }), executor);

                CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);

                assertThat(friendships(pair)).hasSize(1)
                        .allSatisfy(friendship -> assertThat(friendship.getStatus())
                                .isEqualTo(FriendshipStatus.ACCEPTED));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Pair pair() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Region region = Region.builder().name("부산").type(RegionType.REGION).build();
            entityManager.persist(region);
            return new Pair(user("초대한 사람", region).getId(), user("초대받은 사람", region).getId());
        });
    }

    private User user(String name, Region region) {
        User user = User.builder().nickname(name).email(UUID.randomUUID() + "@test.com").gender(Gender.FEMALE)
                .birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build();
        entityManager.persist(user);
        return user;
    }

    private Long persistFriendship(Long requesterId, Long receiverId, boolean accepted) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Friendship friendship = Friendship.request(
                    entityManager.find(User.class, requesterId), entityManager.find(User.class, receiverId));
            if (accepted) {
                friendship.accept();
            }
            entityManager.persist(friendship);
            return friendship.getId();
        });
    }

    private List<Friendship> friendships(Pair pair) {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager.createQuery("""
                        select f from Friendship f join fetch f.requester join fetch f.receiver
                        where f.userLowId = :low and f.userHighId = :high
                        """, Friendship.class)
                .setParameter("low", Math.min(pair.inviterId(), pair.inviteeId()))
                .setParameter("high", Math.max(pair.inviterId(), pair.inviteeId()))
                .getResultList());
    }

    private record Pair(Long inviterId, Long inviteeId) {
    }
}
