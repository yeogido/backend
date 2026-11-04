package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO.ListRequest;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:travel-invitation-list;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TravelRecordInvitationListIntegrationTest {

    @Autowired private TravelRecordInvitationService service;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @Autowired private MockMvc mvc;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;
    private Region region;
    private User owner;
    private User inviter;
    private User receiver;
    private User outsider;
    private final LocalDateTime timestamp = LocalDateTime.of(2026, 10, 1, 10, 0);

    @BeforeEach
    void setUp() {
        region = Region.builder().name("초대 목록 지역").type(RegionType.REGION).build();
        em.persist(region);
        owner = user("생성자", null);
        inviter = user("초대한 친구", "profile/inviter.jpg");
        receiver = user("받은 사람", null);
        outsider = user("외부인", null);
        when(s3Service.getImageUrl(anyString())).thenAnswer(call -> "https://example.com/" + call.getArgument(0));
    }

    @Test
    void returnsOnlyOwnPendingInvitationsWithBasicFolderInfoAcrossYears() throws Exception {
        Long id = invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp);
        invitation(receiver, TravelRecordInvitationStatus.ACCEPTED, timestamp.plusDays(1));
        invitation(receiver, TravelRecordInvitationStatus.DECLINED, timestamp.plusDays(2));
        invitation(outsider, TravelRecordInvitationStatus.PENDING, timestamp.plusDays(3));
        flushAndClear();
        mvc.perform(get("/api/v1/travel-records/invitations").with(auth(receiver.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].invitationId").value(id))
                .andExpect(jsonPath("$.result.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.result.items[0].owner.userId").value(owner.getId()))
                .andExpect(jsonPath("$.result.items[0].owner.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.result.items[0].inviter.userId").value(inviter.getId()))
                .andExpect(jsonPath("$.result.items[0].inviter.profileImageUrl").value("https://example.com/profile/inviter.jpg"))
                .andExpect(jsonPath("$.result.items[0].travelRecord.startDate").value("2024-05-01"))
                .andExpect(jsonPath("$.result.items[0].travelRecord.folderTheme").value("BASIC"))
                .andExpect(jsonPath("$.result.items[0].travelRecord.coverImageUrl").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].travelRecord.coverImageKey").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].travelRecord.images").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].travelRecord.imageKey").doesNotExist())
                .andExpect(jsonPath("$.result.cursorValue").isEmpty())
                .andExpect(jsonPath("$.result.cursorId").value(id))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void pagesByCreationTimeThenIdWithoutSkippingSameTimestamp() {
        Long older = invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp.minusSeconds(1));
        Long first = invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp);
        Long second = invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp);
        flushAndClear();
        var page1 = service.getReceivedInvitations(receiver.getId(), new ListRequest(null, 1));
        assertThat(page1.getItems()).extracting(item -> item.invitationId()).containsExactly(second);
        assertThat(page1.isHasNext()).isTrue();
        var page2 = service.getReceivedInvitations(receiver.getId(), new ListRequest(page1.getCursorId(), 1));
        assertThat(page2.getItems()).extracting(item -> item.invitationId()).containsExactly(first);
        assertThat(page2.isHasNext()).isTrue();
        var page3 = service.getReceivedInvitations(receiver.getId(), new ListRequest(page2.getCursorId(), 1));
        assertThat(page3.getItems()).extracting(item -> item.invitationId()).containsExactly(older);
        assertThat(page3.isHasNext()).isFalse();
        var empty = service.getReceivedInvitations(receiver.getId(), new ListRequest(page3.getCursorId(), 1));
        assertThat(empty.getItems()).isEmpty();
        assertThat(empty.getCursorId()).isNull();
        assertThat(empty.isHasNext()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TravelRecordInvitationStatus.class, names = {"ACCEPTED", "DECLINED"})
    void respondedCursorRemainsUsable(TravelRecordInvitationStatus response) {
        Long older = invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp.minusDays(1));
        Long cursor = invitation(receiver, response, timestamp);
        flushAndClear();
        assertThat(service.getReceivedInvitations(receiver.getId(), new ListRequest(cursor, 10)).getItems())
                .extracting(item -> item.invitationId()).containsExactly(older);
    }

    @Test
    void rejectsAnotherRecipientsCursorAndMissingCursor() {
        Long foreign = invitation(outsider, TravelRecordInvitationStatus.PENDING, timestamp);
        flushAndClear();
        assertInvalid(new ListRequest(foreign, 10));
        assertInvalid(new ListRequest(Long.MAX_VALUE, 10));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, Integer.MAX_VALUE})
    void rejectsInvalidSize(int size) throws Exception {
        assertInvalid(new ListRequest(null, size));
        mvc.perform(get("/api/v1/travel-records/invitations").param("size", String.valueOf(size))
                        .with(auth(receiver.getId())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRAVEL_RECORD4006"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsInvalidCursor(long cursor) throws Exception {
        assertInvalid(new ListRequest(cursor, 10));
        mvc.perform(get("/api/v1/travel-records/invitations").param("cursor", String.valueOf(cursor))
                        .with(auth(receiver.getId())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRAVEL_RECORD4006"));
    }

    @Test
    void defaultsToTenAndSupportsMaximumPageSize() {
        for (int index = 0; index < 11; index++) {
            invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp.plusSeconds(index));
        }
        flushAndClear();
        var page = service.getReceivedInvitations(receiver.getId(), new ListRequest(null, null));
        assertThat(page.getItems()).hasSize(10);
        assertThat(page.isHasNext()).isTrue();
        assertThat(service.getReceivedInvitations(receiver.getId(), new ListRequest(null, 100)).getItems()).hasSize(11);
    }

    @Test
    void creationTimeTakesPriorityOverLargerId() {
        Long newer = invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp);
        invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp.minusDays(1));
        flushAndClear();
        assertThat(service.getReceivedInvitations(receiver.getId(), new ListRequest(null, 1)).getItems())
                .extracting(item -> item.invitationId()).containsExactly(newer);
    }

    @ParameterizedTest
    @EnumSource(value = TravelRecordVisibility.class, names = {"PRIVATE", "PUBLIC"})
    void excludesAlbumsThatAreNotFriends(TravelRecordVisibility visibility) {
        Long id = invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp);
        em.createQuery("update TravelRecord r set r.visibility = :visibility where r.id = "
                        + "(select i.travelRecord.id from TravelRecordInvitation i where i.id = :id)")
                .setParameter("visibility", visibility).setParameter("id", id).executeUpdate();
        em.clear();
        assertThat(service.getReceivedInvitations(receiver.getId(), new ListRequest(null, null)).getItems()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"INACTIVE", "DELETED"})
    void excludesInactiveOwners(UserStatus status) {
        invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp);
        em.flush();
        em.createQuery("update User u set u.status = :status where u.id = :id")
                .setParameter("status", status).setParameter("id", owner.getId()).executeUpdate();
        em.clear();
        assertThat(service.getReceivedInvitations(receiver.getId(), new ListRequest(null, null)).getItems()).isEmpty();
    }

    @Test
    void deletingAlbumRemovesItsPendingInvitation() {
        Long id = invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp);
        em.flush();
        em.remove(em.find(TravelRecordInvitation.class, id).getTravelRecord());
        flushAndClear();
        assertThat(service.getReceivedInvitations(receiver.getId(), new ListRequest(null, null)).getItems()).isEmpty();
    }

    @Test
    void fetchesFolderAndProfilesWithoutQueriesPerInvitation() {
        for (int index = 0; index < 5; index++) {
            invitation(receiver, TravelRecordInvitationStatus.PENDING, timestamp.plusSeconds(index));
        }
        flushAndClear();
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            long before = statistics.getPrepareStatementCount();
            assertThat(service.getReceivedInvitations(receiver.getId(), new ListRequest(null, 10)).getItems()).hasSize(5);
            assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void requiresLoginAndReturnsEmptyListForNoInvitations() throws Exception {
        mvc.perform(get("/api/v1/travel-records/invitations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/travel-records/invitations").with(auth(receiver.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items").isEmpty())
                .andExpect(jsonPath("$.result.cursorId").isEmpty())
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    private Long invitation(User recipient, TravelRecordInvitationStatus status, LocalDateTime createdAt) {
        TravelRecord album = TravelRecord.builder().user(owner).region(region).title("지난 여행")
                .startDate(LocalDate.of(2024, 5, 1)).endDate(LocalDate.of(2024, 5, 3))
                .coverImageKey("travel-records/private-photo.jpg").folderTheme(FolderTheme.BASIC)
                .visibility(TravelRecordVisibility.FRIENDS).build();
        em.persist(album);
        var invitation = TravelRecordInvitation.builder().travelRecord(album).inviter(inviter)
                .invitee(recipient).status(status).build();
        em.persist(invitation);
        em.flush();
        em.createQuery("update TravelRecordInvitation i set i.createdAt = :time where i.id = :id")
                .setParameter("time", createdAt).setParameter("id", invitation.getId()).executeUpdate();
        return invitation.getId();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private User user(String nickname, String profileImage) {
        User user = User.builder().nickname(nickname).email(UUID.randomUUID() + "@test.com")
                .gender(Gender.FEMALE).birthYear("2000").region(region).role(UserRole.USER)
                .status(UserStatus.ACTIVE).profileImage(profileImage).build();
        em.persist(user);
        return user;
    }

    private RequestPostProcessor auth(Long id) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthUser(id, UserRole.USER, "invitation-list-test"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private void assertInvalid(ListRequest request) {
        assertThatThrownBy(() -> service.getReceivedInvitations(receiver.getId(), request))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TravelRecordErrorCode.INVALID_PAGE_REQUEST));
    }
}
