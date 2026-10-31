package com.yeogido.backend.domain.friend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.config.JpaConfig;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:friendship-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class FriendshipRepositoryTest {

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Region region;

    @BeforeEach
    void setUp() {
        region = entityManager.persist(Region.builder()
                .name("서울")
                .fullName("서울특별시")
                .type(RegionType.REGION)
                .build());
    }

    @Test
    void rejectsSecondFriendshipForSamePairInReverseDirection() {
        User a = persistUser("a", UserStatus.ACTIVE);
        User b = persistUser("b", UserStatus.ACTIVE);
        friendshipRepository.saveAndFlush(Friendship.request(a, b));

        assertThatThrownBy(() -> friendshipRepository.saveAndFlush(Friendship.request(b, a)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsFriendshipByPairAndAcceptedStatus() {
        User a = persistUser("a", UserStatus.ACTIVE);
        User b = persistUser("b", UserStatus.ACTIVE);
        Friendship friendship = friendshipRepository.saveAndFlush(Friendship.request(b, a));
        Long low = Math.min(a.getId(), b.getId());
        Long high = Math.max(a.getId(), b.getId());

        assertThat(friendshipRepository.findByUserLowIdAndUserHighId(low, high)).contains(friendship);
        assertThat(friendshipRepository.existsByUserLowIdAndUserHighIdAndStatus(low, high, FriendshipStatus.ACCEPTED))
                .isFalse();

        friendship.accept();
        friendshipRepository.flush();

        assertThat(friendshipRepository.existsByUserLowIdAndUserHighIdAndStatus(low, high, FriendshipStatus.ACCEPTED))
                .isTrue();
    }

    @Test
    void findsFriendshipByIdWithWriteLock() {
        User a = persistUser("a", UserStatus.ACTIVE);
        User b = persistUser("b", UserStatus.ACTIVE);
        Friendship friendship = friendshipRepository.saveAndFlush(Friendship.request(a, b));
        entityManager.clear();

        Friendship locked = friendshipRepository.findByIdForUpdate(friendship.getId()).orElseThrow();

        assertThat(locked.getId()).isEqualTo(friendship.getId());
        assertThat(entityManager.getEntityManager().getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(friendshipRepository.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    void pendingListsExcludeAcceptedAndInactiveUsersAndApplyCursor() {
        User me = persistUser("me", UserStatus.ACTIVE);
        User first = persistUser("first", UserStatus.ACTIVE);
        User second = persistUser("second", UserStatus.ACTIVE);
        User withdrawn = persistUser("withdrawn", UserStatus.DELETED);
        User friend = persistUser("friend", UserStatus.ACTIVE);

        Friendship fromFirst = friendshipRepository.save(Friendship.request(first, me));
        Friendship fromSecond = friendshipRepository.save(Friendship.request(second, me));
        friendshipRepository.save(Friendship.request(withdrawn, me));
        Friendship accepted = friendshipRepository.save(Friendship.request(friend, me));
        accepted.accept();
        Friendship toWithdrawn = Friendship.request(me, persistUser("gone", UserStatus.DELETED));
        friendshipRepository.save(toWithdrawn);
        Friendship toFirstOther = friendshipRepository.save(Friendship.request(me, persistUser("other", UserStatus.ACTIVE)));
        friendshipRepository.flush();

        List<Friendship> received = friendshipRepository.findPendingReceived(me.getId(), null, PageRequest.of(0, 10));
        assertThat(received).containsExactly(fromSecond, fromFirst);

        List<Friendship> nextPage = friendshipRepository.findPendingReceived(
                me.getId(), fromSecond.getId(), PageRequest.of(0, 10));
        assertThat(nextPage).containsExactly(fromFirst);

        List<Friendship> sent = friendshipRepository.findPendingSent(me.getId(), null, PageRequest.of(0, 10));
        assertThat(sent).containsExactly(toFirstOther);
    }

    private User persistUser(String name, UserStatus status) {
        return entityManager.persistFlushFind(User.builder()
                .nickname(name)
                .email(name + "@example.com")
                .gender(Gender.MALE)
                .birthYear("1990")
                .region(region)
                .role(UserRole.USER)
                .status(status)
                .build());
    }
}