package com.yeogido.backend.domain.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.service.TravelRecordService;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 트랜잭션으로 READ_COMMITTED 경로와 실패 시 전체 롤백을 확인합니다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:friend-delete-transaction;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class FriendDeleteTransactionTest {

    @Autowired private FriendService friendService;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private TravelRecordService travelRecordService;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;

    @Test
    void commitsCleanupAndFriendshipDeletionTogether() {
        Fixture fixture = fixture();

        friendService.deleteFriend(fixture.me().getId(), fixture.friend().getId());

        assertThat(friendService.areFriends(fixture.me().getId(), fixture.friend().getId())).isFalse();
        assertThat(exists(TravelRecord.class, fixture.myAlbumId())).isFalse();
        assertThat(memberCount(fixture.friendAlbumId())).isZero();
    }

    @Test
    void rollsBackEverythingWhenCleanupFails() {
        Fixture fixture = fixture();
        RuntimeException failure = new RuntimeException("정리 실패");
        doThrow(failure).when(travelRecordService)
                .leaveTravelRecord(fixture.friendAlbumId(), fixture.me().getId());

        assertThatThrownBy(() -> friendService.deleteFriend(fixture.me().getId(), fixture.friend().getId()))
                .isSameAs(failure);

        assertThat(friendService.areFriends(fixture.me().getId(), fixture.friend().getId())).isTrue();
        assertThat(exists(TravelRecord.class, fixture.myAlbumId())).isTrue();
        assertThat(memberCount(fixture.myAlbumId())).isEqualTo(1);
    }

    /** 앨범 ID 순서상 삭제가 나가기보다 먼저 실행되도록 내 앨범을 먼저 만듭니다. */
    private Fixture fixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Region region = Region.builder().name("부산").type(RegionType.REGION).build();
            entityManager.persist(region);
            User me = user("나", region);
            User friend = user("친구", region);
            Friendship friendship = Friendship.request(me, friend);
            friendship.accept();
            entityManager.persist(friendship);

            TravelRecord myAlbum = album(me, region);
            member(myAlbum, friend);
            TravelRecord friendAlbum = album(friend, region);
            member(friendAlbum, me);
            entityManager.flush();
            return new Fixture(me, friend, myAlbum.getId(), friendAlbum.getId());
        });
    }

    private User user(String name, Region region) {
        User user = User.builder().nickname(name).email(UUID.randomUUID() + "@test.com").gender(Gender.FEMALE)
                .birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build();
        entityManager.persist(user);
        return user;
    }

    private TravelRecord album(User owner, Region region) {
        TravelRecord album = TravelRecord.builder().user(owner).region(region).title("여행")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
                .folderTheme(FolderTheme.BASIC).visibility(TravelRecordVisibility.FRIENDS)
                .coverImageKey("travel-records/original.jpg").build();
        entityManager.persist(album);
        return album;
    }

    private void member(TravelRecord album, User user) {
        entityManager.persist(TravelRecordMember.builder().travelRecord(album).user(user).build());
    }

    private boolean exists(Class<?> type, Long id) {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager.find(type, id) != null);
    }

    private long memberCount(Long albumId) {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager.createQuery(
                        "select count(m) from TravelRecordMember m where m.travelRecord.id = :id", Long.class)
                .setParameter("id", albumId)
                .getSingleResult());
    }

    private record Fixture(User me, User friend, Long myAlbumId, Long friendAlbumId) {
    }
}
