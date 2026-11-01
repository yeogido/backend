package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.entity.Sticker;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.entity.TravelRecordMember;
import com.yeogido.backend.domain.travel.entity.TravelRecordPhoto;
import com.yeogido.backend.domain.travel.entity.TravelRecordSticker;
import com.yeogido.backend.domain.travel.enums.FolderTheme;
import com.yeogido.backend.domain.travel.enums.StickerCategory;
import com.yeogido.backend.domain.travel.enums.StickerType;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.travel.exception.TravelRecordErrorCode;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 스티커 오류가 임시 사진을 소모하지 않고 별도 트랜잭션의 재시도를 허용하는지 검증합니다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:travel-sticker-validation;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class TravelRecordStickerValidationTransactionTest {

  @Autowired private TravelRecordService service;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private FileService fileService;
  @MockitoBean private S3Service s3Service;

  private Long regionId;
  private Long participantId;
  private Long albumId;
  private Long defaultStickerId;
  private Long foreignStickerId;
  private Long deletedStickerId;
  private String temporaryKey;
  private final Set<String> temporaryPhotos = ConcurrentHashMap.newKeySet();

  @BeforeEach
  void setUp() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      Region region = Region.builder().name("테스트 지역").type(RegionType.REGION).build();
      entityManager.persist(region);
      regionId = region.getId();
      User owner = user(region);
      User participant = user(region);
      participantId = participant.getId();
      Sticker defaultSticker = sticker(null, StickerType.DEFAULT);
      defaultStickerId = defaultSticker.getId();
      foreignStickerId = sticker(owner, StickerType.CUSTOM).getId();
      Sticker deleted = sticker(participant, StickerType.CUSTOM);
      deleted.delete();
      deletedStickerId = deleted.getId();
      TravelRecord album = TravelRecord.builder().user(owner).region(region).title("원래 제목")
          .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
          .folderTheme(FolderTheme.BASIC).visibility(TravelRecordVisibility.FRIENDS)
          .coverImageKey("travel-records/original.jpg").build();
      entityManager.persist(album);
      albumId = album.getId();
      entityManager.persist(TravelRecordMember.builder().travelRecord(album).user(participant).build());
      entityManager.persist(TravelRecordPhoto.builder().travelRecord(album)
          .imageKey("travel-records/original.jpg").imageOrder(1).build());
      entityManager.persist(TravelRecordSticker.builder().travelRecord(album).sticker(defaultSticker)
          .positionX(1.0).positionY(1.0).rotation(0.0).scale(1.0).zIndex(0).build());
    });
    temporaryKey = "temp/" + UUID.randomUUID() + ".jpg";
    temporaryPhotos.add(temporaryKey);
    // 파일을 한 번 이동하면 같은 임시 key는 사용할 수 없는 동작을 모사합니다.
    when(fileService.moveToDirectory(anyString(), any())).thenAnswer(call -> {
      String key = call.getArgument(0);
      if (!temporaryPhotos.remove(key)) throw new IllegalStateException("임시 사진이 이미 이동되었습니다.");
      return "travel-records/" + key.substring("temp/".length());
    });
    when(s3Service.getImageUrl(anyString())).thenAnswer(call -> "https://example.com/" + call.getArgument(0));
  }

  /** 등록 실패 후에도 같은 임시 사진으로 스티커만 수정하여 다시 등록할 수 있습니다. */
  @ParameterizedTest
  @EnumSource(InvalidSticker.class)
  void invalidCreateDoesNotMovePhotoAndAllowsRetry(InvalidSticker invalid) {
    long before = recordCount();
    assertError(() -> service.createTravelRecord(participantId, createRequest(invalidStickers(invalid))), invalid);
    verify(fileService, never()).moveToDirectory(anyString(), any());
    assertThat(recordCount()).isEqualTo(before);
    assertThat(temporaryPhotos).contains(temporaryKey);

    var response = service.createTravelRecord(participantId, createRequest(List.of(placement(defaultStickerId, 0))));
    assertThat(response.version()).isZero();
    var detail = service.getTravelRecord(participantId, response.travelRecordId());
    assertThat(detail.images()).hasSize(1);
    assertThat(detail.stickers()).hasSize(1);
    assertThat(detail.images().getFirst().imageKey()).startsWith("travel-records/");
    verify(fileService, times(1)).moveToDirectory(anyString(), any());
  }

  /** 수정 실패는 버전 선점까지 롤백하고 기존 콘텐츠와 임시 사진을 모두 유지합니다. */
  @ParameterizedTest
  @EnumSource(InvalidSticker.class)
  void invalidUpdateKeepsOriginalStateAndAllowsSameVersionRetry(InvalidSticker invalid) {
    var before = service.getTravelRecord(participantId, albumId);
    assertError(() -> service.updateTravelRecord(albumId, participantId, updateRequest(invalidStickers(invalid))), invalid);
    verify(fileService, never()).moveToDirectory(anyString(), any());
    assertThat(service.getTravelRecord(participantId, albumId)).isEqualTo(before);
    assertThat(temporaryPhotos).contains(temporaryKey);

    var response = service.updateTravelRecord(albumId, participantId, updateRequest(List.of(placement(defaultStickerId, 1))));
    assertThat(response.version()).isEqualTo(1L);
    var detail = service.getTravelRecord(participantId, albumId);
    assertThat(detail.title()).isEqualTo("수정 제목");
    assertThat(detail.images().getFirst().imageKey()).startsWith("travel-records/");
    assertThat(detail.stickers()).hasSize(1);
    verify(fileService, times(1)).moveToDirectory(anyString(), any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  void omittedStickersStayAndEmptyStickersAreRemoved(List<TravelRecordReqDTO.StickerRequest> stickers) {
    service.updateTravelRecord(albumId, participantId, updateRequest(stickers));
    assertThat(service.getTravelRecord(participantId, albumId).stickers()).hasSize(stickers == null ? 1 : 0);
    verify(fileService, times(1)).moveToDirectory(anyString(), any());
  }

  private List<TravelRecordReqDTO.StickerRequest> invalidStickers(InvalidSticker invalid) {
    return switch (invalid) {
      case MISSING -> List.of(placement(Long.MAX_VALUE, 0));
      case FOREIGN -> List.of(placement(foreignStickerId, 0));
      case DELETED -> List.of(placement(deletedStickerId, 0));
      case DUPLICATE_Z_INDEX -> List.of(placement(defaultStickerId, 0), placement(defaultStickerId, 0));
    };
  }

  private void assertError(Runnable action, InvalidSticker invalid) {
    TravelRecordErrorCode expected = invalid == InvalidSticker.DUPLICATE_Z_INDEX
        ? TravelRecordErrorCode.INVALID_STICKER_POSITION : TravelRecordErrorCode.STICKER_NOT_FOUND;
    assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }

  private long recordCount() {
    return new TransactionTemplate(transactionManager).execute(status ->
        entityManager.createQuery("select count(t) from TravelRecord t", Long.class).getSingleResult());
  }

  private TravelRecordReqDTO.CreateRequest createRequest(List<TravelRecordReqDTO.StickerRequest> stickers) {
    return new TravelRecordReqDTO.CreateRequest("등록 제목", regionId, LocalDate.of(2026, 5, 1),
        LocalDate.of(2026, 5, 3), "BASIC", images(), stickers, "PRIVATE");
  }

  private TravelRecordReqDTO.UpdateRequest updateRequest(List<TravelRecordReqDTO.StickerRequest> stickers) {
    return new TravelRecordReqDTO.UpdateRequest(0L, "수정 제목", regionId, LocalDate.of(2026, 5, 1),
        LocalDate.of(2026, 5, 3), "BASIC", images(), stickers);
  }

  private List<TravelRecordReqDTO.ImageRequest> images() {
    return List.of(new TravelRecordReqDTO.ImageRequest(temporaryKey, 1));
  }

  private TravelRecordReqDTO.StickerRequest placement(Long stickerId, int zIndex) {
    return new TravelRecordReqDTO.StickerRequest(stickerId, 1.0, 1.0, 0.0, 1.0, zIndex);
  }

  private Sticker sticker(User owner, StickerType type) {
    Sticker sticker = Sticker.builder().user(owner).name("검증 스티커").imageKey("stickers/" + UUID.randomUUID())
        .stickerType(type).category(StickerCategory.values()[0]).build();
    entityManager.persist(sticker);
    return sticker;
  }

  private User user(Region region) {
    User user = User.builder().nickname("스티커 테스트").email(UUID.randomUUID() + "@test.com")
        .gender(Gender.FEMALE).birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build();
    entityManager.persist(user);
    return user;
  }

  enum InvalidSticker { MISSING, FOREIGN, DELETED, DUPLICATE_Z_INDEX }
}
