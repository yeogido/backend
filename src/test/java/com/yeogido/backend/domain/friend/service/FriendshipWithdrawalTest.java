package com.yeogido.backend.domain.friend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yeogido.backend.domain.auth.service.RefreshTokenService;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.friend.repository.FriendshipRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.service.UserService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 회원 탈퇴가 친구 관계와 그 관계에 연결된 대기 초대를 실제 트랜잭션에서 정리하는지 확인합니다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:friendship-withdrawal;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class FriendshipWithdrawalTest {

    @Autowired private UserService userService;
    @Autowired private FriendshipRepository friendshipRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private RefreshTokenService refreshTokenService;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;

    @Test
    void withdrawalRemovesOnlyFriendshipsAndPendingInvitationsOfTheUser() {
        Fixture fixture = fixture();

        userService.withdraw(fixture.withdrawing());

        assertThat(friendshipExists(fixture.asRequester())).isFalse();
        assertThat(friendshipExists(fixture.asReceiver())).isFalse();
        assertThat(friendshipExists(fixture.pendingRequest())).isFalse();
        assertThat(friendshipExists(fixture.others())).isTrue();
        assertThat(invitationExists(fixture.linkedInvitation())).isFalse();
        assertThat(invitationExists(fixture.othersInvitation())).isTrue();
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Region region = Region.builder().name("부산").type(RegionType.REGION).build();
            entityManager.persist(region);
            User withdrawing = user("탈퇴자", region);
            User friend = user("친구", region);
            User third = user("제3자", region);
            User requester = user("요청자", region);

            Friendship asRequester = friendship(withdrawing, friend, true);
            Friendship asReceiver = friendship(third, withdrawing, true);
            Friendship pendingRequest = friendship(requester, withdrawing, false);
            Friendship others = friendship(friend, third, true);

            TravelRecord album = TravelRecord.builder().user(friend).region(region).title("여행")
                    .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
                    .folderTheme(FolderTheme.BASIC).visibility(TravelRecordVisibility.FRIENDS)
                    .coverImageKey("travel-records/original.jpg").build();
            entityManager.persist(album);
            Long linkedInvitation = invitation(album, friend, withdrawing, asRequester);
            Long othersInvitation = invitation(album, friend, third, others);
            entityManager.flush();

            return new Fixture(withdrawing.getId(), asRequester.getId(), asReceiver.getId(),
                    pendingRequest.getId(), others.getId(), linkedInvitation, othersInvitation);
        });
    }

    private User user(String name, Region region) {
        User user = User.builder().nickname(name).email(UUID.randomUUID() + "@test.com").gender(Gender.FEMALE)
                .birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build();
        entityManager.persist(user);
        return user;
    }

    private Friendship friendship(User requester, User receiver, boolean accepted) {
        Friendship friendship = Friendship.request(requester, receiver);
        if (accepted) {
            friendship.accept();
        }
        entityManager.persist(friendship);
        return friendship;
    }

    private Long invitation(TravelRecord album, User inviter, User invitee, Friendship friendship) {
        TravelRecordInvitation invitation = TravelRecordInvitation.builder().travelRecord(album).inviter(inviter)
                .invitee(invitee).status(TravelRecordInvitationStatus.PENDING).friendship(friendship).build();
        entityManager.persist(invitation);
        return invitation.getId();
    }

    private boolean friendshipExists(Long id) {
        return friendshipRepository.existsById(id);
    }

    private boolean invitationExists(Long id) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> entityManager.find(TravelRecordInvitation.class, id) != null);
    }

    private record Fixture(
            Long withdrawing,
            Long asRequester,
            Long asReceiver,
            Long pendingRequest,
            Long others,
            Long linkedInvitation,
            Long othersInvitation
    ) {
    }
}
