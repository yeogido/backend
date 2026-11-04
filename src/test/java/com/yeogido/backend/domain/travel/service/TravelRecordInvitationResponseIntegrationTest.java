package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordInvitationResDTO;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.travel.repository.TravelRecordInvitationRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.exception.ErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:travel-invitation-response;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TravelRecordInvitationResponseIntegrationTest {

    @Autowired private TravelRecordInvitationService service;
    @Autowired private TravelRecordService recordService;
    @Autowired private TravelRecordInvitationRepository invitations;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mvc;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;
    private Long ownerId;
    private Long inviterId;
    private Long receiverId;
    private Long outsiderId;
    private Long albumId;
    private Long friendshipId;
    private Long invitationId;

    @BeforeEach
    void setUp() {
        tx(() -> {
            Region region = Region.builder().name("응답 테스트 지역").type(RegionType.REGION).build();
            em.persist(region);
            User owner = user(region);
            User inviter = user(region);
            User receiver = user(region);
            User outsider = user(region);
            ownerId = owner.getId();
            inviterId = inviter.getId();
            receiverId = receiver.getId();
            outsiderId = outsider.getId();
            Friendship friendship = Friendship.request(inviter, receiver);
            friendship.accept();
            em.persist(friendship);
            friendshipId = friendship.getId();
            TravelRecord album = TravelRecord.builder().user(owner).region(region).title("초대 수락 테스트")
                    .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
                    .coverImageKey("travel-records/response.jpg").visibility(TravelRecordVisibility.FRIENDS).build();
            em.persist(album);
            albumId = album.getId();
            em.persist(TravelRecordMember.builder().travelRecord(album).user(inviter).build());
            em.persist(TravelRecordPhoto.builder().travelRecord(album).imageKey("travel-records/response.jpg")
                    .imageOrder(1).build());
            return null;
        });
        when(s3Service.getImageUrl(anyString())).thenAnswer(call -> "https://example.com/" + call.getArgument(0));
        invitationId = invite();
    }

    @Test
    void acceptsAtomicallyAndGrantsReadAccessWhileRemovingPendingFolder() {
        assertThatThrownBy(() -> recordService.getTravelRecord(receiverId, albumId)).isInstanceOf(GeneralException.class);
        var response = respond("ACCEPTED");
        assertThat(response.invitationId()).isEqualTo(invitationId);
        assertThat(response.travelRecordId()).isEqualTo(albumId);
        assertThat(response.status()).isEqualTo(TravelRecordInvitationStatus.ACCEPTED);
        assertThat(memberCount()).isEqualTo(1);
        assertThat(recordService.getTravelRecord(receiverId, albumId).version()).isEqualTo(1);
        assertThat(service.getReceivedInvitations(receiverId, new TravelRecordInvitationReqDTO.ListRequest(null, 10))
                .getItems()).isEmpty();
        assertThat(tx(() -> invitations.findById(invitationId).orElseThrow().getFriendship())).isNull();
    }

    @Test
    void declinesWithoutMembershipAndCanBeReinvitedWithNewId() {
        respond("DECLINED");
        assertThat(memberCount()).isZero();
        assertThat(recordService.getTravelRecord(ownerId, albumId).version()).isZero();
        assertThat(service.getReceivedInvitations(receiverId, new TravelRecordInvitationReqDTO.ListRequest(null, 10))
                .getItems()).isEmpty();
        Long newer = invite();
        assertThat(newer).isGreaterThan(invitationId);
        assertThat(invitations.findById(invitationId)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "DECLINED"})
    void sameResponseIsIdempotentAndOppositeResponseIsConflict(String status) {
        respond(status);
        long version = recordService.getTravelRecord(ownerId, albumId).version();
        assertThat(respond(status).status().name()).isEqualTo(status);
        assertThat(recordService.getTravelRecord(ownerId, albumId).version()).isEqualTo(version);
        assertError(receiverId, status.equals("ACCEPTED") ? "DECLINED" : "ACCEPTED",
                TravelRecordErrorCode.INVITATION_ALREADY_PROCESSED);
    }

    @Test
    void leavingCannotBeUndoneWithOldAcceptedInvitationButNewInvitationWorks() {
        respond("ACCEPTED");
        recordService.leaveTravelRecord(albumId, receiverId);
        assertError(receiverId, "ACCEPTED", TravelRecordErrorCode.INVITATION_ALREADY_PROCESSED);
        invitationId = invite();
        respond("ACCEPTED");
        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void onlyRecipientCanRespondAndMissingInvitationIsNotFound() throws Exception {
        assertError(outsiderId, "ACCEPTED", GeneralErrorCode.FORBIDDEN);
        mvc.perform(patch(path()).with(auth(ownerId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DECLINED\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMON4031"));
        invitationId = Long.MAX_VALUE;
        assertError(receiverId, "ACCEPTED", TravelRecordErrorCode.INVITATION_NOT_FOUND);
        assertThat(memberCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"PENDING\"}",
            "{\"status\":\"accepted\"}", "{\"status\":\"\"}"})
    void rejectsInvalidResponseBodies(String body) throws Exception {
        mvc.perform(patch(path()).with(auth(receiverId)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(memberCount()).isZero();
        assertThat(invitations.findById(invitationId).orElseThrow().getStatus())
                .isEqualTo(TravelRecordInvitationStatus.PENDING);
    }

    @Test
    void apiRequiresLoginAndReturnsAcceptedResponse() throws Exception {
        mvc.perform(patch(path()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(path()).with(auth(receiverId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.invitationId").value(invitationId))
                .andExpect(jsonPath("$.result.travelRecordId").value(albumId))
                .andExpect(jsonPath("$.result.status").value("ACCEPTED"));
    }

    @Test
    void friendRemovalDeletesPendingInvitationAndRefriendingDoesNotRestoreIt() {
        removeFriendship();
        assertThat(invitations.findById(invitationId)).isEmpty();
        assertError(receiverId, "ACCEPTED", TravelRecordErrorCode.INVITATION_NOT_FOUND);
        assertThat(service.getReceivedInvitations(receiverId, new TravelRecordInvitationReqDTO.ListRequest(null, 10))
                .getItems()).isEmpty();
        tx(() -> {
            Friendship friend = Friendship.request(em.getReference(User.class, inviterId), em.getReference(User.class, receiverId));
            friend.accept();
            em.persist(friend);
            return null;
        });
        assertThat(invitations.findById(invitationId)).isEmpty();
        invitationId = invite();
        respond("ACCEPTED");
        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void friendRemovalAfterResponseDoesNotDeleteParticipationOrCompletedResponse() {
        respond("ACCEPTED");
        removeFriendship();
        assertThat(memberCount()).isEqualTo(1);
        assertThat(respond("ACCEPTED").status()).isEqualTo(TravelRecordInvitationStatus.ACCEPTED);
        assertThat(recordService.getTravelRecord(receiverId, albumId).travelRecordId()).isEqualTo(albumId);
    }

    @Test
    void inviterLeavingDoesNotInvalidateInvitationWhileFriendshipRemains() {
        recordService.leaveTravelRecord(albumId, inviterId);
        respond("ACCEPTED");
        assertThat(memberCount()).isEqualTo(1);
    }

    @Test
    void albumDeletionMakesInvitationUnavailable() {
        recordService.deleteTravelRecord(albumId, ownerId);
        assertError(receiverId, "ACCEPTED", TravelRecordErrorCode.INVITATION_NOT_FOUND);
        assertThat(memberCount()).isZero();
    }

    @Test
    void simultaneousAcceptsOnlyCreateOneMembership() throws Exception {
        List<String> results = race(() -> result("ACCEPTED"), () -> result("ACCEPTED"));
        assertThat(results).containsExactlyInAnyOrder("OK", "OK");
        assertThat(memberCount()).isEqualTo(1);
        assertThat(recordService.getTravelRecord(ownerId, albumId).version()).isEqualTo(1);
    }

    @Test
    void simultaneousOppositeResponsesOnlyApplyOne() throws Exception {
        List<String> results = race(() -> result("ACCEPTED"), () -> result("DECLINED"));
        assertThat(results).containsExactlyInAnyOrder("OK", "TRAVEL_RECORD4094");
        var status = invitations.findById(invitationId).orElseThrow().getStatus();
        assertThat(memberCount()).isEqualTo(status == TravelRecordInvitationStatus.ACCEPTED ? 1 : 0);
    }

    @Test
    void concurrentAlbumDeletionLeavesNoOrphanMembership() throws Exception {
        race(() -> result("ACCEPTED"), () -> { recordService.deleteTravelRecord(albumId, ownerId); return "DELETED"; });
        assertThat(invitations.findById(invitationId)).isEmpty();
        assertThat(memberCount()).isZero();
    }

    @Test
    void concurrentFriendRemovalEitherCancelsPendingOrPreservesAcceptedMembership() throws Exception {
        var results = race(() -> result("ACCEPTED"), () -> { removeFriendship(); return "REMOVED"; });
        assertThat(results).contains("REMOVED");
        if (results.contains("OK")) {
            assertThat(memberCount()).isEqualTo(1);
            assertThat(invitations.findById(invitationId).orElseThrow().getStatus())
                    .isEqualTo(TravelRecordInvitationStatus.ACCEPTED);
        } else {
            assertThat(results).contains("TRAVEL_RECORD4042");
            assertThat(memberCount()).isZero();
            assertThat(invitations.findById(invitationId)).isEmpty();
        }
    }

    @Test
    void retryRacingWithLeavingCannotRestoreMembership() throws Exception {
        respond("ACCEPTED");
        race(() -> result("ACCEPTED"), () -> { recordService.leaveTravelRecord(albumId, receiverId); return "LEFT"; });
        assertThat(memberCount()).isZero();
        assertError(receiverId, "ACCEPTED", TravelRecordErrorCode.INVITATION_ALREADY_PROCESSED);
    }

    @Test
    void inactiveInviterCannotGrantNewMembership() {
        tx(() -> {
            em.createQuery("update User u set u.status = :status where u.id = :id")
                    .setParameter("status", UserStatus.DELETED).setParameter("id", inviterId).executeUpdate();
            return null;
        });
        assertError(receiverId, "ACCEPTED", TravelRecordErrorCode.INVITATION_NOT_FOUND);
        assertThat(memberCount()).isZero();
    }

    @Test
    void inactiveRecipientCannotRespond() {
        tx(() -> {
            em.createQuery("update User u set u.status = :status where u.id = :id")
                    .setParameter("status", UserStatus.INACTIVE).setParameter("id", receiverId).executeUpdate();
            return null;
        });
        assertError(receiverId, "ACCEPTED", GeneralErrorCode.FORBIDDEN);
        assertThat(memberCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"recipient", "inviter"})
    void rejectsDeactivationCommittedAfterUserWasAlreadyLoaded(String target) {
        Long targetId = target.equals("recipient") ? receiverId : inviterId;
        ErrorCode expected = target.equals("recipient") ? GeneralErrorCode.FORBIDDEN
                : TravelRecordErrorCode.INVITATION_NOT_FOUND;
        assertThatThrownBy(() -> tx(() -> {
            User cached = em.find(User.class, targetId);
            assertThat(cached.getStatus()).isEqualTo(UserStatus.ACTIVE);
            // 다른 트랜잭션의 변경이 영속성 컨텍스트에 남아 있는 ACTIVE 값보다 우선해야 합니다.
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> tx(() -> {
                    em.createQuery("update User u set u.status = :status where u.id = :id")
                            .setParameter("status", UserStatus.INACTIVE).setParameter("id", targetId).executeUpdate();
                    return null;
                })).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            assertThat(cached.getStatus()).isEqualTo(UserStatus.ACTIVE);
            return respond("ACCEPTED");
        })).isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        assertThat(memberCount()).isZero();
        assertThat(invitations.findById(invitationId).orElseThrow().getStatus())
                .isEqualTo(TravelRecordInvitationStatus.PENDING);
    }

    private List<String> race(Supplier<String> first, Supplier<String> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return first.get(); });
            var b = executor.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return second.get(); });
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private String result(String response) {
        try { respond(response); return "OK"; }
        catch (GeneralException e) { return e.getErrorCode().getCode(); }
    }

    private TravelRecordInvitationResDTO.RespondResponse respond(String status) {
        return service.respondToInvitation(invitationId, receiverId, new TravelRecordInvitationReqDTO.RespondRequest(status));
    }

    private Long invite() {
        return service.createInvitations(albumId, inviterId, new TravelRecordInvitationReqDTO.CreateRequest(List.of(receiverId)))
                .invitations().getFirst().invitationId();
    }

    private long memberCount() {
        return tx(() -> em.createQuery("select count(m) from TravelRecordMember m where m.travelRecord.id = :id and m.user.id = :user", Long.class)
                .setParameter("id", albumId).setParameter("user", receiverId).getSingleResult());
    }

    private void removeFriendship() {
        tx(() -> { em.remove(em.find(Friendship.class, friendshipId)); return null; });
    }

    private void assertError(Long userId, String status, ErrorCode code) {
        assertThatThrownBy(() -> service.respondToInvitation(invitationId, userId,
                new TravelRecordInvitationReqDTO.RespondRequest(status)))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    private String path() { return "/api/v1/travel-records/invitations/" + invitationId; }

    private RequestPostProcessor auth(Long id) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthUser(id, UserRole.USER, "response-test"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private User user(Region region) {
        User user = User.builder().nickname("응답 테스트").email(UUID.randomUUID() + "@test.com")
                .gender(Gender.FEMALE).birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build();
        em.persist(user);
        return user;
    }
}
