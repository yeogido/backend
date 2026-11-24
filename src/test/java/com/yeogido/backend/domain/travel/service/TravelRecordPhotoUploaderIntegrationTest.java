package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.yeogido.backend.domain.auth.service.RefreshTokenService;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.service.UserService;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 저장, 공동 편집, 나가기 및 회원탈퇴를 통한 사진 업로더 수명 주기를 검증합니다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:travel-photo-uploader;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
    "logging.level.org.hibernate.SQL=OFF"
})
@ActiveProfiles("test")
class TravelRecordPhotoUploaderIntegrationTest {
  @Autowired private TravelRecordService service;
  @Autowired private TravelRecordShareService shareService;
  @Autowired private UserService userService;
  @Autowired private EntityManager em;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private FileService fileService;
  @MockitoBean private S3Service s3Service;
  @MockitoBean private RefreshTokenService refreshTokenService;
  private Long ownerId;
  private Long participantId;
  private Long regionId;
  private Long albumId;
  private static final LocalDate START = LocalDate.of(2026, 5, 1);

  @BeforeEach
  void setUp() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      Region region = Region.builder().name("부산").type(RegionType.REGION).build();
      em.persist(region);
      regionId = region.getId();
      User owner = user("생성자", region);
      User participant = user("참여자", region);
      ownerId = owner.getId();
      participantId = participant.getId();
      TravelRecord album = album(owner, region);
      albumId = album.getId();
      em.persist(TravelRecordMember.builder().travelRecord(album).user(participant).build());
      photo(album, owner, "travel-records/owner.jpg", 1);
      photo(album, participant, "travel-records/participant.jpg", 2);
      photo(album, null, "travel-records/legacy.jpg", 3);
    });
    when(fileService.moveToDirectory(anyString(), any())).thenAnswer(call -> call.getArgument(0));
    when(s3Service.getImageUrl(anyString())).thenAnswer(call -> "https://example.com/" + call.getArgument(0));
  }

  @Test
  void creationAttributesPhotosToAuthenticatedCreator() {
    var created = service.createTravelRecord(ownerId, new TravelRecordReqDTO.CreateRequest(
        "새 기록", regionId, START, START.plusDays(2), "BASIC", List.of(image("new", 1)),
        List.of(), "PUBLIC"));
    var detail = service.getTravelRecord(ownerId, created.travelRecordId());
    assertThat(detail.images().getFirst().uploader().userId()).isEqualTo(ownerId);
    assertThat(detail.images().getFirst().uploader().profileImageUrl()).endsWith("profiles/생성자.jpg");
  }

  @Test
  void coeditingAndReorderingKeepOriginalAndUnknownUploaders() {
    service.updateTravelRecord(albumId, participantId, new TravelRecordReqDTO.UpdateRequest(
        0L, "수정", regionId, START, START.plusDays(2), "BASIC",
        List.of(image("legacy", 1), image("owner", 2), image("new", 3), image("participant", 4)), null));
    var detail = service.getTravelRecord(ownerId, albumId);
    assertThat(detail.images().get(0).uploader()).isNull();
    assertThat(detail.images().get(1).uploader().userId()).isEqualTo(ownerId);
    assertThat(detail.images().get(2).uploader().userId()).isEqualTo(participantId);
    assertThat(detail.images().get(3).uploader().userId()).isEqualTo(participantId);
  }

  @Test
  void leavingRemovesParticipantButKeepsPhotoAndUploaderProfile() {
    service.leaveTravelRecord(albumId, participantId);
    var detail = service.getTravelRecord(ownerId, albumId);
    assertThat(detail.participants()).isEmpty();
    assertThat(detail.images()).hasSize(3);
    assertThat(detail.images().get(1).uploader().userId()).isEqualTo(participantId);
    assertThat(detail.images().get(1).uploader().profileImageUrl()).endsWith("profiles/참여자.jpg");
  }

  @Test
  void withdrawalAfterLeavingRemovesPhotosAndInvalidatesOldEditVersion() {
    service.leaveTravelRecord(albumId, participantId);
    long version = service.getTravelRecord(ownerId, albumId).version();
    userService.withdraw(participantId);
    var detail = service.getTravelRecord(ownerId, albumId);
    assertThat(detail.images()).extracting(image -> image.imageKey())
        .containsExactly("travel-records/owner.jpg", "travel-records/legacy.jpg");
    assertThat(detail.images()).extracting(image -> image.imageOrder()).containsExactly(1, 2);
    assertThat(detail.images().getFirst().uploader().userId()).isEqualTo(ownerId);
    assertThat(detail.images().getLast().uploader()).isNull();
    assertThat(detail.version()).isGreaterThan(version);
    assertThatThrownBy(() -> service.updateTravelRecord(albumId, ownerId,
        new TravelRecordReqDTO.UpdateRequest(version, "이전 화면", regionId, START, START.plusDays(2),
            "BASIC", List.of(image("participant", 1)), null)))
        .isInstanceOfSatisfying(GeneralException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TravelRecordErrorCode.VERSION_CONFLICT));
  }

  @Test
  void withdrawalReplacesDeletedCoverButRetainsOwnerAndLegacyPhotos() {
    userService.withdraw(makeParticipantCoverUploader());
    var detail = service.getTravelRecord(ownerId, albumId);
    assertThat(detail.visibility()).isEqualTo(TravelRecordVisibility.PUBLIC);
    assertThat(detail.coverImageUrl()).endsWith("travel-records/owner.jpg");
    assertThat(detail.images()).extracting(image -> image.imageOrder()).containsExactly(1, 2);
  }

  // 생성자 계정은 유지하고 참여자가 대표 사진을 올린 상태를 구성합니다.
  private Long makeParticipantCoverUploader() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      em.createQuery("delete from TravelRecordPhoto photo where photo.travelRecord.id = :id")
          .setParameter("id", albumId).executeUpdate();
      var album = em.find(TravelRecord.class, albumId);
      photo(album, em.find(User.class, participantId), "travel-records/participant.jpg", 1);
      photo(album, em.find(User.class, ownerId), "travel-records/owner.jpg", 2);
      photo(album, null, "travel-records/legacy.jpg", 3);
      album.updateCoverImageKey("travel-records/participant.jpg");
    });
    return participantId;
  }

  @Test
  void withdrawalOfOnlyPhotoUploaderKeepsEmptyAlbumWithNoCover() {
    Long emptyId = new TransactionTemplate(transactionManager).execute(status -> {
      var album = album(em.find(User.class, ownerId), em.find(Region.class, regionId));
      em.persist(TravelRecordMember.builder().travelRecord(album).user(em.find(User.class, participantId)).build());
      photo(album, em.find(User.class, participantId), "travel-records/only.jpg", 1);
      return album.getId();
    });
    userService.withdraw(participantId);
    var detail = service.getTravelRecord(ownerId, emptyId);
    assertThat(detail.images()).isEmpty();
    assertThat(detail.coverImageUrl()).isNull();
    assertThat(detail.participants()).isEmpty();
    assertThat(detail.visibility()).isEqualTo(TravelRecordVisibility.PUBLIC);
  }

  @Test
  void linkPreviewAndMapIncludeUploaderWithoutGrantingEditRights() {
    Long visitor = new TransactionTemplate(transactionManager).execute(status ->
        user("방문자", em.find(Region.class, regionId)).getId());
    var token = shareService.createLink(albumId, ownerId).token();
    var preview = shareService.getSharedRecord(token, visitor);
    assertThat(preview.detail()).isNull();
    assertThat(preview.canAccept()).isTrue();
    assertThat(preview.preview().previewImages().get(1).uploader().userId()).isEqualTo(participantId);
    var map = service.getMyTravelMap(ownerId, new TravelRecordReqDTO.MapRequest(null, 10, 2026, null));
    assertThat(map.getItems().getFirst().images().get(1).uploader().userId()).isEqualTo(participantId);
  }

  private TravelRecordReqDTO.ImageRequest image(String name, int order) {
    return new TravelRecordReqDTO.ImageRequest("travel-records/" + name + ".jpg", order);
  }

  private User user(String name, Region region) {
    User user = User.builder().nickname(name).email(UUID.randomUUID() + "@test.com").gender(Gender.FEMALE)
        .birthYear("2000").region(region).profileImage("profiles/" + name + ".jpg")
        .role(UserRole.USER).status(UserStatus.ACTIVE).build();
    em.persist(user);
    return user;
  }

  private TravelRecord album(User owner, Region region) {
    var album = TravelRecord.builder().user(owner).region(region).title("공유 기록")
        .startDate(START).endDate(START.plusDays(2)).folderTheme(FolderTheme.BASIC)
        .visibility(TravelRecordVisibility.FRIENDS).coverImageKey("travel-records/owner.jpg").build();
    em.persist(album);
    return album;
  }

  private void photo(TravelRecord album, User uploader, String key, int order) {
    em.persist(TravelRecordPhoto.builder().travelRecord(album).uploader(uploader)
        .imageKey(key).imageOrder(order).build());
  }
}
