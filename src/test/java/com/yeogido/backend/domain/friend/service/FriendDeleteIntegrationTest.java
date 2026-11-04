package com.yeogido.backend.domain.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.exception.FriendErrorCode;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.service.TravelRecordInvitationServiceImpl;
import com.yeogido.backend.domain.travel.service.TravelRecordServiceImpl;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.config.JpaConfig;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, FriendServiceImpl.class, TravelRecordServiceImpl.class, TravelRecordInvitationServiceImpl.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:friend-delete-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
class FriendDeleteIntegrationTest {

    @Autowired private FriendService friendService;
    @Autowired private TravelRecordInvitationServiceImpl invitationService;
    @Autowired private TestEntityManager em;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;

    private Region region;
    private User me;
    private User friend;
    private User third;
    private Friendship friendship;

    @BeforeEach
    void setUp() {
        region = em.persist(Region.builder().name("부산").type(RegionType.REGION).build());
        me = user("나", UserStatus.ACTIVE);
        friend = user("친구", UserStatus.ACTIVE);
        third = user("제3자", UserStatus.ACTIVE);
        friendship = acceptedFriendship(friend, me);
    }

    @Test
    void deletesFriendshipInBothDirections() {
        delete();

        assertThat(friendService.areFriends(me.getId(), friend.getId())).isFalse();
        assertThat(friendService.areFriends(friend.getId(), me.getId())).isFalse();
    }

    @Test
    void deletesMyAlbumWhenFriendIsTheOnlyMember() {
        TravelRecord album = album(me);
        member(album, friend);
        photo(album);

        delete();

        assertThat(em.find(TravelRecord.class, album.getId())).isNull();
        assertThat(count("TravelRecordPhoto", album)).isZero();
        assertThat(count("TravelRecordMember", album)).isZero();
    }

    @Test
    void excludesOnlyFriendFromMyAlbumWhenAnotherMemberExists() {
        TravelRecord album = album(me);
        member(album, friend);
        member(album, third);
        photo(album);
        Long version = album.getVersion();

        delete();

        TravelRecord found = em.find(TravelRecord.class, album.getId());
        assertThat(found).isNotNull();
        assertThat(found.getVersion()).isEqualTo(version + 1);
        assertThat(members(album)).containsExactly(third.getId());
        assertThat(count("TravelRecordPhoto", album)).isEqualTo(1);
    }

    @Test
    void withdrawnMemberIsNotCountedAsAnotherMember() {
        TravelRecord album = album(me);
        member(album, friend);
        member(album, user("탈퇴자", UserStatus.DELETED));

        delete();

        assertThat(em.find(TravelRecord.class, album.getId())).isNull();
    }

    @Test
    void leavesFriendsAlbumAndKeepsItForOthers() {
        TravelRecord album = album(friend);
        member(album, me);
        member(album, third);
        photo(album);

        delete();

        assertThat(em.find(TravelRecord.class, album.getId())).isNotNull();
        assertThat(members(album)).containsExactly(third.getId());
        assertThat(count("TravelRecordPhoto", album)).isEqualTo(1);
    }

    @Test
    void leavesThirdPartyAlbumOnlyForMe() {
        TravelRecord album = album(third);
        member(album, me);
        member(album, friend);

        delete();

        assertThat(members(album)).containsExactly(friend.getId());
    }

    @Test
    void removesOnlyPendingInvitationsBetweenTheTwoUsers() {
        TravelRecord myAlbum = album(me);
        TravelRecordInvitation pendingToFriend = invitation(myAlbum, me, friend,
                TravelRecordInvitationStatus.PENDING, friendship);
        Friendship thirdFriendship = acceptedFriendship(me, third);
        TravelRecordInvitation pendingToThird = invitation(myAlbum, me, third,
                TravelRecordInvitationStatus.PENDING, thirdFriendship);
        TravelRecord friendAlbum = album(friend);
        TravelRecordInvitation pendingToMe = invitation(friendAlbum, friend, me,
                TravelRecordInvitationStatus.PENDING, friendship);
        TravelRecordInvitation declined = invitation(album(friend), friend, me,
                TravelRecordInvitationStatus.DECLINED, null);

        delete();

        assertThat(em.find(TravelRecord.class, myAlbum.getId())).isNotNull();
        assertThat(em.find(TravelRecord.class, friendAlbum.getId())).isNotNull();
        assertThat(em.find(TravelRecordInvitation.class, pendingToFriend.getId())).isNull();
        assertThat(em.find(TravelRecordInvitation.class, pendingToMe.getId())).isNull();
        assertThat(em.find(TravelRecordInvitation.class, pendingToThird.getId())).isNotNull();
        assertThat(em.find(TravelRecordInvitation.class, declined.getId())).isNotNull();
    }

    @Test
    void keepsAlbumsThatAreNotSharedByTheTwoUsers() {
        TravelRecord myPrivate = album(me, TravelRecordVisibility.PRIVATE);
        TravelRecord myPublic = album(me, TravelRecordVisibility.PUBLIC);
        TravelRecord myWithThird = album(me);
        member(myWithThird, third);
        TravelRecord friendsOwn = album(friend);
        TravelRecord friendWithThird = album(friend);
        member(friendWithThird, third);
        TravelRecord thirdWithMe = album(third);
        member(thirdWithMe, me);

        delete();

        for (TravelRecord album : List.of(myPrivate, myPublic, myWithThird, friendsOwn, friendWithThird, thirdWithMe)) {
            assertThat(em.find(TravelRecord.class, album.getId())).isNotNull();
        }
        assertThat(members(myWithThird)).containsExactly(third.getId());
        assertThat(members(thirdWithMe)).containsExactly(me.getId());
    }

    @Test
    void canInviteAgainAfterExcludedFriendBecomesFriendAgain() {
        TravelRecord album = album(me);
        member(album, friend);
        member(album, third);
        invitation(album, me, friend, TravelRecordInvitationStatus.ACCEPTED, null);

        delete();
        acceptedFriendship(me, friend);
        flushAndClear();

        var response = invitationService.createInvitations(album.getId(), me.getId(),
                new TravelRecordInvitationReqDTO.CreateRequest(List.of(friend.getId())));

        assertThat(response).isNotNull();
        assertThat(count("TravelRecordInvitation", album)).isEqualTo(1);
    }

    @Test
    void rejectsWhenThereIsNoAcceptedFriendship() {
        User stranger = user("모르는 사람", UserStatus.ACTIVE);
        User requested = user("요청 중", UserStatus.ACTIVE);
        em.persist(Friendship.request(me, requested));
        User withdrawn = user("탈퇴한 친구", UserStatus.DELETED);
        acceptedFriendship(me, withdrawn);
        flushAndClear();

        for (Long targetId : List.of(stranger.getId(), requested.getId(), me.getId(), withdrawn.getId(), 999_999L)) {
            assertThatThrownBy(() -> friendService.deleteFriend(me.getId(), targetId))
                    .isInstanceOfSatisfying(GeneralException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(FriendErrorCode.NOT_FRIEND));
        }
    }

    private void delete() {
        flushAndClear();
        friendService.deleteFriend(me.getId(), friend.getId());
        flushAndClear();
    }

    private User user(String name, UserStatus status) {
        return em.persist(User.builder().nickname(name).email(UUID.randomUUID() + "@test.com").gender(Gender.FEMALE)
                .birthYear("2000").region(region).role(UserRole.USER).status(status).build());
    }

    private Friendship acceptedFriendship(User requester, User receiver) {
        Friendship accepted = Friendship.request(requester, receiver);
        accepted.accept();
        return em.persist(accepted);
    }

    private TravelRecord album(User owner) {
        return album(owner, TravelRecordVisibility.FRIENDS);
    }

    private TravelRecord album(User owner, TravelRecordVisibility visibility) {
        return em.persistAndFlush(TravelRecord.builder().user(owner).region(region).title("여행")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
                .folderTheme(FolderTheme.BASIC).visibility(visibility)
                .coverImageKey("travel-records/original.jpg").build());
    }

    private void member(TravelRecord album, User user) {
        em.persist(TravelRecordMember.builder().travelRecord(album).user(user).build());
    }

    private void photo(TravelRecord album) {
        em.persist(TravelRecordPhoto.builder().travelRecord(album)
                .imageKey("travel-records/original.jpg").imageOrder(1).build());
    }

    private TravelRecordInvitation invitation(
            TravelRecord album,
            User inviter,
            User invitee,
            TravelRecordInvitationStatus status,
            Friendship linkedFriendship
    ) {
        return em.persist(TravelRecordInvitation.builder().travelRecord(album).inviter(inviter).invitee(invitee)
                .status(status).friendship(linkedFriendship).build());
    }

    private List<Long> members(TravelRecord album) {
        return em.getEntityManager().createQuery(
                        "select m.user.id from TravelRecordMember m where m.travelRecord.id = :id", Long.class)
                .setParameter("id", album.getId())
                .getResultList();
    }

    private long count(String type, TravelRecord album) {
        return em.getEntityManager().createQuery(
                        "select count(e) from " + type + " e where e.travelRecord.id = :id", Long.class)
                .setParameter("id", album.getId())
                .getSingleResult();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
