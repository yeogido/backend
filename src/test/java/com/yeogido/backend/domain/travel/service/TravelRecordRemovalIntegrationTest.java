package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.entity.TravelRecordSticker;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.StickerCategory;
import com.yeogido.backend.domain.travel.enums.StickerType;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.config.JpaConfig;
import com.yeogido.backend.global.exception.ErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
@Import({JpaConfig.class, TravelRecordServiceImpl.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:travel-removal-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
class TravelRecordRemovalIntegrationTest {
  @Autowired private TravelRecordService service;
  @Autowired private TestEntityManager em;
  @MockitoBean private FileService fileService;
  @MockitoBean private S3Service s3Service;
  private Region region;
  private User owner;
  private User participant;
  private User other;

  @BeforeEach
  void setUp() {
    region = em.persist(Region.builder().name("부산").type(RegionType.REGION).build());
    owner = user("생성자");
    participant = user("참여자");
    other = user("다른 참여자");
    when(s3Service.getImageUrl(anyString())).thenAnswer(call -> "https://example.com/" + call.getArgument(0));
    when(fileService.moveToDirectory(anyString(), any())).thenAnswer(call -> call.getArgument(0));
  }

  /** 대상 앨범의 모든 자식 관계만 정리되고 다른 앨범과 스티커 원본은 유지되는지 확인합니다. */
  @ParameterizedTest
  @EnumSource(TravelRecordVisibility.class)
  void ownerDeletesOnlyTargetAlbumAndCascadesItsRelations(TravelRecordVisibility visibility) {
    TravelRecord target = album(visibility);
    member(target, participant);
    member(target, other);
    invitation(target, participant, TravelRecordInvitationStatus.ACCEPTED);
    invitation(target, other, TravelRecordInvitationStatus.PENDING);
    Sticker source = contents(target);
    TravelRecord retained = album(TravelRecordVisibility.FRIENDS);
    member(retained, participant);
    invitation(retained, participant, TravelRecordInvitationStatus.ACCEPTED);
    contents(retained);
    clear();
    service.deleteTravelRecord(target.getId(), owner.getId());
    clear();

    assertThat(em.find(TravelRecord.class, target.getId())).isNull();
    for (String type : List.of("TravelRecordPhoto", "TravelRecordSticker", "TravelRecordMember", "TravelRecordInvitation")) {
      assertThat(count(type, target.getId())).isZero();
      assertThat(count(type, retained.getId())).isEqualTo(1);
    }
    assertThat(em.find(TravelRecord.class, retained.getId())).isNotNull();
    assertThat(em.find(Sticker.class, source.getId())).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(TravelRecordVisibility.class)
  void anotherUserCannotDeleteEvenIfTheyCanRead(TravelRecordVisibility visibility) {
    TravelRecord target = album(visibility);
    member(target, participant);
    contents(target);
    clear();
    assertError(() -> service.deleteTravelRecord(target.getId(), participant.getId()), GeneralErrorCode.FORBIDDEN);
    assertThat(count("TravelRecordPhoto", target.getId())).isEqualTo(1);
    assertThat(count("TravelRecordMember", target.getId())).isEqualTo(1);
  }

  @Test
  void leavingKeepsContentsOtherMembersAndAcceptedInvitationButRemovesAccess() {
    TravelRecord target = album(TravelRecordVisibility.FRIENDS);
    member(target, participant);
    member(target, other);
    invitation(target, participant, TravelRecordInvitationStatus.ACCEPTED);
    Sticker source = contents(target);
    clear();
    service.leaveTravelRecord(target.getId(), participant.getId());
    clear();

    var detail = service.getTravelRecord(owner.getId(), target.getId());
    assertThat(detail.version()).isEqualTo(1L);
    assertThat(detail.participants()).extracting(TravelRecordResDTO.UserProfile::userId).containsExactly(other.getId());
    assertThat(detail.images()).hasSize(1);
    assertThat(detail.stickers()).hasSize(1);
    assertThat(em.find(Sticker.class, source.getId())).isNotNull();
    assertThat(count("TravelRecordInvitation", target.getId())).isEqualTo(1);
    assertThat(service.getMyTravelRecords(participant.getId(),
        new TravelRecordReqDTO.ListRequest(null, 10, 2026, null, null)).getItems()).isEmpty();
    assertThat(service.getMyTravelRecordYears(participant.getId(),
        new TravelRecordReqDTO.YearListRequest(null, null)).years()).isEmpty();
    assertError(() -> service.getTravelRecord(participant.getId(), target.getId()), GeneralErrorCode.FORBIDDEN);
    assertError(() -> service.updateTravelRecord(target.getId(), participant.getId(), request(0L)), GeneralErrorCode.FORBIDDEN);
    assertError(() -> service.leaveTravelRecord(target.getId(), participant.getId()), GeneralErrorCode.FORBIDDEN);
  }

  @ParameterizedTest
  @EnumSource(TravelRecordVisibility.class)
  void ownerCannotLeaveAnyVisibility(TravelRecordVisibility visibility) {
    TravelRecord target = album(visibility);
    clear();
    assertError(() -> service.leaveTravelRecord(target.getId(), owner.getId()), TravelRecordErrorCode.OWNER_CANNOT_LEAVE);
    assertThat(em.find(TravelRecord.class, target.getId()).getVersion()).isZero();
  }

  @ParameterizedTest
  @EnumSource(TravelRecordInvitationStatus.class)
  void invitationWithoutCurrentMembershipDoesNotAllowLeaving(TravelRecordInvitationStatus status) {
    TravelRecord target = album(TravelRecordVisibility.FRIENDS);
    invitation(target, participant, status);
    clear();
    assertError(() -> service.leaveTravelRecord(target.getId(), participant.getId()), GeneralErrorCode.FORBIDDEN);
    assertThat(count("TravelRecordInvitation", target.getId())).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(value = TravelRecordVisibility.class, names = {"PRIVATE", "PUBLIC"})
  void staleMembershipOfNonSharedAlbumCannotBeRemovedThroughLeave(TravelRecordVisibility visibility) {
    TravelRecord target = album(visibility);
    member(target, participant);
    clear();
    assertError(() -> service.leaveTravelRecord(target.getId(), participant.getId()), GeneralErrorCode.FORBIDDEN);
    assertThat(count("TravelRecordMember", target.getId())).isEqualTo(1);
  }

  @Test
  void withdrawnParticipantCannotLeaveOrDelete() {
    TravelRecord target = album(TravelRecordVisibility.FRIENDS);
    member(target, participant);
    participant.withdraw();
    clear();
    assertError(() -> service.leaveTravelRecord(target.getId(), participant.getId()), GeneralErrorCode.FORBIDDEN);
    assertError(() -> service.deleteTravelRecord(target.getId(), participant.getId()), GeneralErrorCode.FORBIDDEN);
  }

  @Test
  void nonExistingAlbumReturns404ForBothOperations() {
    assertError(() -> service.deleteTravelRecord(Long.MAX_VALUE, owner.getId()), TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND);
    assertError(() -> service.leaveTravelRecord(Long.MAX_VALUE, participant.getId()), TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND);
  }

  @Test
  void withdrawnOwnersAlbumCannotBeLeft() {
    TravelRecord target = album(TravelRecordVisibility.FRIENDS);
    member(target, participant);
    owner.withdraw();
    clear();
    assertError(() -> service.leaveTravelRecord(target.getId(), participant.getId()), TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND);
  }

  @Test
  void lastParticipantMayLeaveWithoutChangingVisibilityAndOldOwnerVersionConflicts() {
    TravelRecord target = album(TravelRecordVisibility.FRIENDS);
    member(target, participant);
    contents(target);
    clear();
    service.leaveTravelRecord(target.getId(), participant.getId());
    clear();
    var detail = service.getTravelRecord(owner.getId(), target.getId());
    assertThat(detail.participants()).isEmpty();
    assertThat(detail.visibility()).isEqualTo(TravelRecordVisibility.FRIENDS);
    assertError(() -> service.updateTravelRecord(target.getId(), owner.getId(), request(0L)),
        TravelRecordErrorCode.VERSION_CONFLICT);
  }

  private User user(String name) {
    return em.persist(User.builder().nickname(name).email(UUID.randomUUID() + "@test.com").gender(Gender.FEMALE)
        .birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build());
  }

  private TravelRecord album(TravelRecordVisibility visibility) {
    return em.persist(TravelRecord.builder().user(owner).region(region).title("여행")
        .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
        .folderTheme(FolderTheme.BASIC).visibility(visibility).coverImageKey("travel-records/original.jpg").build());
  }

  private void member(TravelRecord target, User user) {
    em.persist(TravelRecordMember.builder().travelRecord(target).user(user).build());
  }

  private void invitation(TravelRecord target, User invitee, TravelRecordInvitationStatus status) {
    em.persist(TravelRecordInvitation.builder().travelRecord(target).inviter(owner).invitee(invitee).status(status).build());
  }

  private Sticker contents(TravelRecord target) {
    em.persist(TravelRecordPhoto.builder().travelRecord(target).imageKey("travel-records/original.jpg").imageOrder(1).build());
    Sticker sticker = em.persist(Sticker.builder().user(participant).name("참여자의 스티커")
        .imageKey("stickers/" + UUID.randomUUID() + ".png").stickerType(StickerType.CUSTOM)
        .category(StickerCategory.values()[0]).build());
    em.persist(TravelRecordSticker.builder().travelRecord(target).sticker(sticker)
        .positionX(1.0).positionY(1.0).rotation(0.0).scale(1.0).zIndex(0).build());
    return sticker;
  }

  private TravelRecordReqDTO.UpdateRequest request(Long version) {
    return new TravelRecordReqDTO.UpdateRequest(version, "수정", region.getId(), LocalDate.of(2026, 5, 1),
        LocalDate.of(2026, 5, 3), "BASIC", List.of(new TravelRecordReqDTO.ImageRequest("travel-records/edited.jpg", 1)), null);
  }

  private long count(String type, Long id) {
    return em.getEntityManager().createQuery("select count(e) from " + type + " e where e.travelRecord.id = :id", Long.class)
        .setParameter("id", id).getSingleResult();
  }

  private void clear() {
    em.flush();
    em.clear();
  }

  private void assertError(Runnable action, ErrorCode code) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(code));
  }
}
