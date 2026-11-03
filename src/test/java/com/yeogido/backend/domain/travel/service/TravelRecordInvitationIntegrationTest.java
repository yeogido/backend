package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.friend.entity.Friendship;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
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
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

/** 실제 트랜잭션으로 초대 권한, 전체 롤백과 같은 앨범의 동시 초대를 검증합니다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:travel-invitation;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TravelRecordInvitationIntegrationTest {

  @Autowired private TravelRecordInvitationService service;
  @Autowired private TravelRecordService recordService;
  @Autowired private TravelRecordInvitationRepository invitations;
  @Autowired private EntityManager em;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @MockitoBean private FileService fileService;
  @MockitoBean private S3Service s3Service;
  private Long ownerId;
  private Long participantId;
  private Long firstId;
  private Long secondId;
  private Long outsiderId;
  private Long albumId;

  @BeforeEach
  void setUp() {
    tx(() -> {
      Region region = Region.builder().name("초대 테스트 지역").type(RegionType.REGION).build();
      em.persist(region);
      User owner = user(region);
      User participant = user(region);
      User first = user(region);
      User second = user(region);
      User outsider = user(region);
      ownerId = owner.getId();
      participantId = participant.getId();
      firstId = first.getId();
      secondId = second.getId();
      outsiderId = outsider.getId();
      acceptedFriend(first, owner);
      acceptedFriend(owner, second);
      acceptedFriend(participant, first);
      TravelRecord album = TravelRecord.builder().user(owner).region(region).title("공유 초대 테스트")
          .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
          .coverImageKey("travel-records/invitation.jpg").visibility(TravelRecordVisibility.FRIENDS).build();
      em.persist(album);
      albumId = album.getId();
      em.persist(TravelRecordMember.builder().travelRecord(album).user(participant).build());
      em.persist(TravelRecordPhoto.builder().travelRecord(album).imageOrder(1)
          .imageKey("travel-records/invitation.jpg").build());
      return null;
    });
    when(s3Service.getImageUrl(anyString())).thenAnswer(call -> "https://example.com/" + call.getArgument(0));
  }

  @Test
  void ownerCanInviteMultipleAcceptedFriendsWithoutGrantingMembershipOrReadAccess() {
    var response = service.createInvitations(albumId, ownerId, request(secondId, firstId));
    assertThat(response.travelRecordId()).isEqualTo(albumId);
    assertThat(response.invitations()).extracting(item -> item.inviteeUserId()).containsExactly(secondId, firstId);
    assertThat(response.invitations()).allSatisfy(item -> {
      assertThat(item.invitationId()).isPositive();
      assertThat(item.status()).isEqualTo(TravelRecordInvitationStatus.PENDING);
      assertThat(item.createdAt()).isNotNull();
    });
    assertThat(countInvitations()).isEqualTo(2);
    assertThat(tx(() -> em.createQuery("select count(m) from TravelRecordMember m where m.travelRecord.id = :id", Long.class)
        .setParameter("id", albumId).getSingleResult())).isEqualTo(1);
    assertThatThrownBy(() -> recordService.getTravelRecord(firstId, albumId))
        .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GeneralErrorCode.FORBIDDEN));
    assertThat(recordService.getTravelRecord(ownerId, albumId).version()).isZero();
  }

  @Test
  void participantInvitationStoresActualInviterAndChecksTheirFriendship() {
    var response = service.createInvitations(albumId, participantId, request(firstId));
    assertThat(tx(() -> invitations.findById(response.invitations().getFirst().invitationId())
        .orElseThrow().getInviter().getId())).isEqualTo(participantId);
    assertError(participantId, request(secondId), TravelRecordErrorCode.INVALID_INVITATION_TARGET);
    assertThat(countInvitations()).isEqualTo(1);
  }

  /** 수락 대기자, 거절한 사용자, 나간 사용자와 외부인은 모두 추가 초대 권한이 없습니다. */
  @ParameterizedTest
  @ValueSource(strings = {"PENDING", "DECLINED", "LEFT", "NONE"})
  void nonParticipantsCannotInvite(String state) {
    if (state.equals("LEFT")) {
      tx(() -> {
        em.persist(TravelRecordMember.builder().travelRecord(em.getReference(TravelRecord.class, albumId))
            .user(em.getReference(User.class, outsiderId)).build());
        return null;
      });
      recordService.leaveTravelRecord(albumId, outsiderId);
    } else if (!state.equals("NONE")) {
      seedInvitation(outsiderId, TravelRecordInvitationStatus.valueOf(state));
    }
    assertError(outsiderId, request(firstId), GeneralErrorCode.FORBIDDEN);
  }

  @ParameterizedTest
  @EnumSource(value = TravelRecordVisibility.class, names = {"PRIVATE", "PUBLIC"})
  void onlyFriendsAlbumsCanReceiveInvitations(TravelRecordVisibility visibility) {
    tx(() -> { em.createQuery("update TravelRecord r set r.visibility = :visibility where r.id = :id")
        .setParameter("visibility", visibility).setParameter("id", albumId).executeUpdate(); return null; });
    assertError(ownerId, request(firstId), TravelRecordErrorCode.INVALID_INVITATION_STATE);
    assertError(participantId, request(firstId), GeneralErrorCode.FORBIDDEN);
  }

  /** 서비스 호출도 HTTP 검증에 의존하지 않고 잘못된 대상 목록 전체를 거부합니다. */
  @Test
  void invalidTargetListsNeverPersistPartialInvitations() {
    for (var request : List.of(new TravelRecordInvitationReqDTO.CreateRequest(null), request(),
        request(firstId, firstId), request(firstId, ownerId), request(firstId, 0L),
        request(firstId, -1L), request(firstId, null), request(firstId, Long.MAX_VALUE), request(firstId, outsiderId))) {
      assertError(ownerId, request, TravelRecordErrorCode.INVALID_INVITATION_TARGET);
      assertThat(countInvitations()).isZero();
    }
  }

  @ParameterizedTest
  @EnumSource(value = UserStatus.class, names = {"INACTIVE", "DELETED"})
  void inactiveTargetsAndRequestersCannotSendOrReceiveInvitations(UserStatus status) {
    setUserStatus(secondId, status);
    assertError(ownerId, request(firstId, secondId), TravelRecordErrorCode.INVALID_INVITATION_TARGET);
    assertThat(countInvitations()).isZero();
    setUserStatus(participantId, status);
    assertError(participantId, request(firstId), GeneralErrorCode.FORBIDDEN);
    setUserStatus(ownerId, status);
    assertError(ownerId, request(firstId), TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND);
    assertError(participantId, request(firstId), TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND);
  }

  @Test
  void pendingFriendRequestDoesNotQualifyAsFriendship() {
    tx(() -> { em.persist(Friendship.request(em.getReference(User.class, ownerId),
        em.getReference(User.class, outsiderId))); return null; });
    assertError(ownerId, request(firstId, outsiderId), TravelRecordErrorCode.INVALID_INVITATION_TARGET);
    assertThat(countInvitations()).isZero();
  }

  @Test
  void pendingDuplicateAndCurrentParticipantRejectWholeBatch() {
    seedInvitation(firstId, TravelRecordInvitationStatus.PENDING);
    assertError(ownerId, request(secondId, firstId), TravelRecordErrorCode.INVITATION_ALREADY_PENDING);
    assertThat(countInvitations()).isEqualTo(1);
    assertError(ownerId, request(secondId, participantId), TravelRecordErrorCode.ALREADY_PARTICIPATING);
    assertError(participantId, request(ownerId), TravelRecordErrorCode.ALREADY_PARTICIPATING);
    assertThat(countInvitations()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(value = TravelRecordInvitationStatus.class, names = {"DECLINED", "ACCEPTED"})
  void reinvitingNonMemberReplacesCompletedInvitationWithFreshPendingRequest(TravelRecordInvitationStatus status) {
    Long oldId = seedInvitation(firstId, status);
    var response = service.createInvitations(albumId, participantId, request(firstId));
    assertThat(response.invitations().getFirst().invitationId()).isNotEqualTo(oldId);
    assertThat(response.invitations().getFirst().status()).isEqualTo(TravelRecordInvitationStatus.PENDING);
    assertThat(invitations.findById(oldId)).isEmpty();
    assertThat(countInvitations()).isEqualTo(1);
  }

  @Test
  void invalidBatchPreservesPreviousCompletedInvitation() {
    Long oldId = seedInvitation(firstId, TravelRecordInvitationStatus.DECLINED);
    assertError(ownerId, request(firstId, outsiderId), TravelRecordErrorCode.INVALID_INVITATION_TARGET);
    assertThat(invitations.findById(oldId).orElseThrow().getStatus()).isEqualTo(TravelRecordInvitationStatus.DECLINED);
  }

  /** 시작 시점을 맞춘 두 실제 요청 중 하나만 저장되고 나머지는 도메인 중복 오류가 됩니다. */
  @Test
  void concurrentInvitersCreateExactlyOnePendingInvitation() throws Exception {
    var together = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> { together.await(10, TimeUnit.SECONDS); return send(ownerId); });
      var second = pool.submit(() -> { together.await(10, TimeUnit.SECONDS); return send(participantId); });
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("OK", "TRAVEL_RECORD4092");
    }
    assertThat(countInvitations()).isEqualTo(1);
  }

  @Test
  void apiReturns201WithSpecifiedResponseShapeAndStillRequiresLogin() throws Exception {
    String body = mapper.writeValueAsString(request(firstId, secondId));
    mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(path()).with(login(ownerId)).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("COMMON201"))
        .andExpect(jsonPath("$.result.travelRecordId").value(albumId))
        .andExpect(jsonPath("$.result.invitations.length()").value(2))
        .andExpect(jsonPath("$.result.invitations[0].inviteeUserId").value(firstId))
        .andExpect(jsonPath("$.result.invitations[0].status").value("PENDING"))
        .andExpect(jsonPath("$.result.invitations[0].createdAt").isNotEmpty());
  }

  /** 목록과 원소의 입력 검증은 400으로 처리하고 잘못된 요청은 저장하지 않습니다. */
  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"inviteeUserIds\":null}", "{\"inviteeUserIds\":[]}",
      "{\"inviteeUserIds\":[null]}", "{\"inviteeUserIds\":[0]}", "{\"inviteeUserIds\":[-1]}"})
  void apiRejectsMalformedTargetLists(String body) throws Exception {
    mvc.perform(post(path()).with(login(ownerId)).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    assertThat(countInvitations()).isZero();
  }

  private String path() { return "/api/v1/travel-records/" + albumId + "/invitations"; }

  private RequestPostProcessor login(Long id) {
    return authentication(new UsernamePasswordAuthenticationToken(new AuthUser(id, UserRole.USER, "invite-test"), null,
        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  private String send(Long userId) {
    try { service.createInvitations(albumId, userId, request(firstId)); return "OK"; }
    catch (GeneralException e) { return e.getErrorCode().getCode(); }
  }

  private void assertError(Long userId, TravelRecordInvitationReqDTO.CreateRequest request,
      com.yeogido.backend.global.exception.ErrorCode error) {
    assertThatThrownBy(() -> service.createInvitations(albumId, userId, request))
        .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode()).isEqualTo(error));
  }

  private long countInvitations() {
    return tx(() -> em.createQuery("select count(i) from TravelRecordInvitation i where i.travelRecord.id = :id", Long.class)
        .setParameter("id", albumId).getSingleResult());
  }

  private Long seedInvitation(Long target, TravelRecordInvitationStatus status) {
    return tx(() -> {
      var invitation = TravelRecordInvitation.builder().travelRecord(em.getReference(TravelRecord.class, albumId))
          .inviter(em.getReference(User.class, ownerId)).invitee(em.getReference(User.class, target)).status(status).build();
      em.persist(invitation);
      return invitation.getId();
    });
  }

  private void setUserStatus(Long id, UserStatus status) {
    tx(() -> { em.createQuery("update User u set u.status = :status where u.id = :id")
        .setParameter("status", status).setParameter("id", id).executeUpdate(); return null; });
  }

  private TravelRecordInvitationReqDTO.CreateRequest request(Long... ids) {
    return new TravelRecordInvitationReqDTO.CreateRequest(Arrays.asList(ids));
  }

  private <T> T tx(Supplier<T> action) {
    return new TransactionTemplate(transactionManager).execute(status -> action.get());
  }

  private void acceptedFriend(User requester, User receiver) {
    var friendship = Friendship.request(requester, receiver);
    friendship.accept();
    em.persist(friendship);
  }

  private User user(Region region) {
    User user = User.builder().nickname("초대 테스트").email(UUID.randomUUID() + "@test.com")
        .gender(Gender.FEMALE).birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build();
    em.persist(user);
    return user;
  }
}
