package com.yeogido.backend.domain.travel.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.config.JpaConfig;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
  "spring.datasource.url=jdbc:h2:mem:travel-sharing-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
class TravelRecordSharingPersistenceTest {

  @Autowired private TestEntityManager entityManager;
  @Autowired private TravelRecordRepository recordRepository;
  @Autowired private TravelRecordMemberRepository memberRepository;
  @Autowired private TravelRecordInvitationRepository invitationRepository;

  private Region region;
  private User owner;
  private User participant;
  private User invitee;

  @BeforeEach
  void setUp() {
    region = entityManager.persist(Region.builder().name("부산").type(RegionType.REGION).build());
    owner = persistUser("생성자");
    participant = persistUser("참여자");
    invitee = persistUser("초대받은 친구");
  }

  @Test
  void existingCreationFlowDefaultsToPrivateAndInitialVersionZero() {
    TravelRecord record = persistRecord(false);
    entityManager.clear();

    TravelRecord saved = recordRepository.findById(record.getId()).orElseThrow();

    assertThat(saved.getVisibility()).isEqualTo(TravelRecordVisibility.PRIVATE);
    assertThat(saved.getVersion()).isZero();
    assertThat(memberRepository.count()).isZero();
    assertThat(invitationRepository.count()).isZero();
  }

  @Test
  void savesCurrentParticipantAndActualInviterSeparatelyFromOwner() {
    TravelRecord record = persistRecord(true);
    TravelRecordMember member = memberRepository.saveAndFlush(TravelRecordMember.builder()
      .travelRecord(record).user(participant).build());
    TravelRecordInvitation invitation = invitationRepository.saveAndFlush(TravelRecordInvitation.builder()
      .travelRecord(record).inviter(participant).invitee(invitee).build());
    entityManager.clear();

    TravelRecordMember savedMember = memberRepository.findById(member.getId()).orElseThrow();
    TravelRecordInvitation savedInvitation = invitationRepository.findById(invitation.getId()).orElseThrow();

    assertThat(recordRepository.findById(record.getId()).orElseThrow().getUser().getId())
      .isEqualTo(owner.getId());
    assertThat(savedMember.getUser().getId()).isEqualTo(participant.getId());
    assertThat(savedMember.getCreatedAt()).isNotNull();
    assertThat(savedInvitation.getInviter().getId()).isEqualTo(participant.getId());
    assertThat(savedInvitation.getInvitee().getId()).isEqualTo(invitee.getId());
    assertThat(savedInvitation.getStatus()).isEqualTo(TravelRecordInvitationStatus.PENDING);
    assertThat(savedInvitation.getCreatedAt()).isNotNull();
    assertThat(savedInvitation.getUpdatedAt()).isNotNull();
    assertThat(memberRepository.count()).isEqualTo(1);
  }

  @Test
  void rejectsDuplicateMemberForSameRecordAndUser() {
    TravelRecord record = persistRecord(true);
    memberRepository.saveAndFlush(TravelRecordMember.builder()
      .travelRecord(record).user(participant).build());

    assertThatThrownBy(() -> memberRepository.saveAndFlush(TravelRecordMember.builder()
      .travelRecord(record).user(participant).build()))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsDuplicateInvitationEvenWhenInviterDiffers() {
    TravelRecord record = persistRecord(true);
    invitationRepository.saveAndFlush(TravelRecordInvitation.builder()
      .travelRecord(record).inviter(owner).invitee(invitee).build());

    assertThatThrownBy(() -> invitationRepository.saveAndFlush(TravelRecordInvitation.builder()
      .travelRecord(record).inviter(participant).invitee(invitee).build()))
      .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deletingMemberKeepsRecordPhotosAndOtherParticipants() {
    TravelRecord record = persistRecord(true);
    TravelRecordPhoto photo = entityManager.persistAndFlush(TravelRecordPhoto.builder()
      .travelRecord(record).imageKey("travel-records/shared.jpg").imageOrder(1).build());
    TravelRecordMember member = memberRepository.saveAndFlush(TravelRecordMember.builder()
      .travelRecord(record).user(participant).build());
    TravelRecordMember otherMember = memberRepository.saveAndFlush(TravelRecordMember.builder()
      .travelRecord(record).user(invitee).build());

    memberRepository.deleteById(member.getId());
    memberRepository.flush();
    entityManager.clear();

    assertThat(recordRepository.existsById(record.getId())).isTrue();
    assertThat(entityManager.find(TravelRecordPhoto.class, photo.getId())).isNotNull();
    assertThat(memberRepository.existsById(otherMember.getId())).isTrue();
    assertThat(entityManager.find(User.class, participant.getId())).isNotNull();
  }

  @Test
  void bulkDeletingOwnedRecordCascadesOnlyItsMembersAndInvitations() {
    TravelRecord record = persistRecord(true);
    memberRepository.saveAndFlush(TravelRecordMember.builder()
      .travelRecord(record).user(participant).build());
    invitationRepository.saveAndFlush(TravelRecordInvitation.builder()
      .travelRecord(record).inviter(participant).invitee(invitee).build());

    assertThat(recordRepository.deleteAllByUserId(owner.getId())).isEqualTo(1);
    entityManager.flush();
    entityManager.clear();

    assertThat(memberRepository.count()).isZero();
    assertThat(invitationRepository.count()).isZero();
    assertThat(entityManager.find(User.class, participant.getId())).isNotNull();
    assertThat(entityManager.find(User.class, invitee.getId())).isNotNull();
  }

  @Test
  void updatingRecordIncrementsVersion() {
    TravelRecord record = persistRecord(false);
    Long originalVersion = record.getVersion();

    updateTitle(record);
    entityManager.flush();
    entityManager.clear();

    assertThat(recordRepository.findById(record.getId()).orElseThrow().getVersion())
      .isEqualTo(originalVersion + 1);
  }

  @Test
  void rejectsUpdateWhenDatabaseVersionChangedAfterLoad() {
    TravelRecord record = persistRecord(false);
    entityManager.getEntityManager().createNativeQuery(
      "UPDATE travel_record SET version = version + 1 WHERE id = :id")
      .setParameter("id", record.getId()).executeUpdate();

    updateTitle(record);

    assertThatThrownBy(() -> entityManager.flush()).isInstanceOf(OptimisticLockException.class);
  }

  private User persistUser(String nickname) {
    return entityManager.persist(User.builder().region(region).nickname(nickname)
      .email(UUID.randomUUID() + "@test.com").gender(Gender.FEMALE).birthYear("2000")
      .role(UserRole.USER).status(UserStatus.ACTIVE).build());
  }

  /**
   * 기본 등록 경로에서는 공개범위를 생략하여 PRIVATE 기본값을 검증하고, 공유 테스트에서는 FRIENDS로 저장합니다.
   *
   * @param shared 공유 앨범 데이터로 저장할지 여부
   * @return 초기 버전과 생성 식별자가 DB에 반영된 여행 기록
   */
  private TravelRecord persistRecord(boolean shared) {
    var builder = TravelRecord.builder().user(owner).region(region).title("부산 여행")
      .startDate(LocalDate.of(2026, 10, 1)).endDate(LocalDate.of(2026, 10, 3))
      .coverImageKey("travel-records/shared.jpg");
    if (shared) {
      builder.visibility(TravelRecordVisibility.FRIENDS);
    }
    return entityManager.persistAndFlush(builder.build());
  }

  private void updateTitle(TravelRecord record) {
    record.update(region, "수정한 부산 여행", record.getStartDate(), record.getEndDate(),
      record.getCoverImageKey(), record.getFolderTheme());
  }
}
