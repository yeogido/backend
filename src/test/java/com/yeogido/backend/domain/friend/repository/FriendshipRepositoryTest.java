package com.yeogido.backend.domain.friend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.enums.FriendshipStatus;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.repository.UserRepository;
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
    private UserRepository userRepository;

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

    @Test
    void findFriendsReturnsAcceptedActiveFriendsInBothDirectionsSortedByName() {
        User me = persistUser("me", UserStatus.ACTIVE);
        User sentByMe = persistUser("banana", UserStatus.ACTIVE);
        User sentToMe = persistUser("apple", UserStatus.ACTIVE);
        User pending = persistUser("pending", UserStatus.ACTIVE);
        User withdrawn = persistUser("withdrawn", UserStatus.DELETED);
        User stranger = persistUser("stranger", UserStatus.ACTIVE);
        User strangersFriend = persistUser("strangers-friend", UserStatus.ACTIVE);

        accept(me, sentByMe);
        accept(sentToMe, me);
        accept(withdrawn, me);
        accept(stranger, strangersFriend);
        friendshipRepository.save(Friendship.request(pending, me));
        friendshipRepository.flush();

        assertThat(findFriends(me, null, null, null, null)).containsExactly(sentToMe, sentByMe);
    }

    @Test
    void findFriendsPagesByNameAndUserIdWhenNamesAreDuplicated() {
        User me = persistUser("me", UserStatus.ACTIVE);
        User firstSame = persistUser("same", "same1@example.com");
        User secondSame = persistUser("same", "same2@example.com");
        User after = persistUser("zebra", UserStatus.ACTIVE);
        accept(me, firstSame);
        accept(me, secondSame);
        accept(me, after);
        friendshipRepository.flush();

        assertThat(findFriends(me, null, null, "same", firstSame.getId())).containsExactly(secondSame, after);
        assertThat(findFriends(me, null, null, "same", secondSame.getId())).containsExactly(after);
    }

    @Test
    void findFriendsMatchesNamePartiallyAndCodeByPrefixOnly() {
        User me = persistUser("me", UserStatus.ACTIVE);
        User named = persistUser("Travel-Mate", UserStatus.ACTIVE);
        User coded = persistUser("other", UserStatus.ACTIVE);
        accept(me, named);
        accept(me, coded);
        friendshipRepository.flush();
        String code = coded.getFriendCode();

        assertThat(findFriends(me, "%el-m%", "EL-M%", null, null)).containsExactly(named);
        assertThat(findFriends(me, "%" + code.toLowerCase() + "%", code + "%", null, null)).contains(coded);
        assertThat(findFriends(me, "%no-such-name%", code.substring(0, 4) + "%", null, null))
                .containsExactly(coded);
        assertThat(findFriends(me, "%no-such-name%", code.substring(1) + "%", null, null)).isEmpty();
    }

    @Test
    void findFriendsTreatsEscapedWildcardsAsLiterals() {
        User me = persistUser("me", UserStatus.ACTIVE);
        User percent = persistUser("100%_real", UserStatus.ACTIVE);
        User plain = persistUser("100xyreal", UserStatus.ACTIVE);
        accept(me, percent);
        accept(me, plain);
        friendshipRepository.flush();

        assertThat(findFriends(me, "%!%%", "!%%", null, null)).containsExactly(percent);
        assertThat(findFriends(me, "%!_%", "!_%", null, null)).containsExactly(percent);
    }

    private List<User> findFriends(
            User user,
            String namePattern,
            String codePattern,
            String cursorNickname,
            Long cursorId
    ) {
        return friendshipRepository.findFriends(
                user.getId(), namePattern, codePattern, cursorNickname, cursorId, PageRequest.of(0, 10));
    }

    @Test
    void findAllBetweenReturnsRelationsInBothDirectionsOnlyForGivenUsers() {
        User me = persistUser("나", "me-between@example.com");
        User sentTo = persistUser("보낸 상대", "sent-between@example.com");
        User receivedFrom = persistUser("받은 상대", "received-between@example.com");
        User excluded = persistUser("제외", "excluded-between@example.com");
        Friendship sent = friendshipRepository.save(Friendship.request(me, sentTo));
        Friendship received = friendshipRepository.save(Friendship.request(receivedFrom, me));
        friendshipRepository.save(Friendship.request(me, excluded));
        friendshipRepository.save(Friendship.request(sentTo, receivedFrom));

        assertThat(friendshipRepository.findAllBetween(me.getId(), List.of(sentTo.getId(), receivedFrom.getId())))
                .containsExactlyInAnyOrder(sent, received);
    }

    @Test
    void findSearchableUsersMatchesExactNameOrCodeAndExcludesMeAndInactive() {
        User me = persistUser("같은이름", "me-search@example.com");
        User sameName = persistUser("같은이름", "same-search@example.com");
        User sameName2 = persistUser("같은이름", "same2-search@example.com");
        persistUser("같은이름 더 긴 이름", "partial-search@example.com");
        persistUser("같은이름", "withdrawn-search@example.com", UserStatus.DELETED);
        User byCode = persistUser("코드로 찾기", "code-search@example.com");

        assertThat(search(me, "같은이름", "같은이름", null, 10)).containsExactly(sameName, sameName2);
        assertThat(search(me, "같은이름", "같은이름", sameName.getId(), 10)).containsExactly(sameName2);
        assertThat(search(me, "같은이름", "같은이름", null, 1)).containsExactly(sameName);
        assertThat(search(me, "없음", byCode.getFriendCode(), null, 10)).containsExactly(byCode);
        assertThat(search(me, "없음", me.getFriendCode(), null, 10)).isEmpty();
        assertThat(search(me, "같은", "같은", null, 10)).isEmpty();
    }

    private List<User> search(User me, String nickname, String friendCode, Long cursorId, int size) {
        return userRepository.findSearchableUsers(me.getId(), nickname, friendCode, cursorId, PageRequest.of(0, size));
    }

    private void accept(User requester, User receiver) {
        friendshipRepository.save(Friendship.request(requester, receiver)).accept();
    }

    private User persistUser(String name, String email) {
        return persistUser(name, email, UserStatus.ACTIVE);
    }

    private User persistUser(String name, UserStatus status) {
        return persistUser(name, name + "@example.com", status);
    }

    private User persistUser(String name, String email, UserStatus status) {
        return entityManager.persistFlushFind(User.builder()
                .nickname(name)
                .email(email)
                .gender(Gender.MALE)
                .birthYear("1990")
                .region(region)
                .role(UserRole.USER)
                .status(status)
                .build());
    }
}