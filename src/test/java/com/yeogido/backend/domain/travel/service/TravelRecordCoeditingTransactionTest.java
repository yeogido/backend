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
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 서로 다른 실제 트랜잭션의 경합과 실패한 저장의 전체 롤백을 검증합니다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:travel-coediting-transaction;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class TravelRecordCoeditingTransactionTest {

  @Autowired private TravelRecordService service;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private FileService fileService;
  @MockitoBean private S3Service s3Service;
  private Long regionId;
  private Long ownerId;
  private Long participantId;
  private Long albumId;

  @BeforeEach
  void setUp() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      Region region = Region.builder().name("테스트 지역").type(RegionType.REGION).build();
      entityManager.persist(region);
      regionId = region.getId();
      User owner = user(region);
      User participant = user(region);
      ownerId = owner.getId();
      participantId = participant.getId();
      TravelRecord album = TravelRecord.builder().user(owner).region(region).title("최초 제목")
          .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
          .folderTheme(FolderTheme.BASIC).visibility(TravelRecordVisibility.FRIENDS)
          .coverImageKey("travel-records/original.jpg").build();
      entityManager.persist(album);
      albumId = album.getId();
      entityManager.persist(TravelRecordMember.builder().travelRecord(album).user(participant).build());
      entityManager.persist(TravelRecordPhoto.builder().travelRecord(album)
          .imageKey("travel-records/original.jpg").imageOrder(1).build());
    });
    when(fileService.moveToDirectory(anyString(), any())).thenAnswer(call -> call.getArgument(0));
    when(s3Service.getImageUrl(anyString())).thenAnswer(call -> "https://example.com/" + call.getArgument(0));
  }

  @Test
  void concurrentSameVersionHasOneWinnerAndPreservesWinnersPhotos() throws Exception {
    CyclicBarrier bothReadVersion = new CyclicBarrier(2);
    when(fileService.moveToDirectory(anyString(), any())).thenAnswer(call -> {
      bothReadVersion.await(10, TimeUnit.SECONDS);
      return call.getArgument(0);
    });
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> save(ownerId, "생성자 변경"));
      var second = pool.submit(() -> save(participantId, "참여자 변경"));
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("OK", "TRAVEL_RECORD4091");
    }
    var detail = service.getTravelRecord(ownerId, albumId);
    assertThat(detail.version()).isEqualTo(1L);
    assertThat(detail.title()).isIn("생성자 변경", "참여자 변경");
    assertThat(detail.images()).hasSize(1);
    assertThat(detail.images().getFirst().imageKey()).isEqualTo("travel-records/" + detail.title() + ".jpg");
  }

  @Test
  void invalidStickerRollsBackReservedVersionMetadataAndReplacedPhotos() {
    var invalidSticker = new TravelRecordReqDTO.StickerRequest(Long.MAX_VALUE, 1.0, 1.0, 0.0, 1.0, 0);
    assertThatThrownBy(() -> service.updateTravelRecord(albumId, participantId,
        request("실패한 변경", List.of(invalidSticker))))
        .isInstanceOfSatisfying(GeneralException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(TravelRecordErrorCode.STICKER_NOT_FOUND));
    var detail = service.getTravelRecord(ownerId, albumId);
    assertThat(detail.version()).isZero();
    assertThat(detail.title()).isEqualTo("최초 제목");
    assertThat(detail.images().getFirst().imageKey()).isEqualTo("travel-records/original.jpg");
  }

  private String save(Long userId, String title) {
    try {
      service.updateTravelRecord(albumId, userId, request(title, null));
      return "OK";
    } catch (GeneralException e) {
      return e.getErrorCode().getCode();
    }
  }

  private TravelRecordReqDTO.UpdateRequest request(String title, List<TravelRecordReqDTO.StickerRequest> stickers) {
    return new TravelRecordReqDTO.UpdateRequest(0L, title, regionId, LocalDate.of(2026, 5, 1),
        LocalDate.of(2026, 5, 3), "BASIC",
        List.of(new TravelRecordReqDTO.ImageRequest("travel-records/" + title + ".jpg", 1)), stickers);
  }

  private User user(Region region) {
    User user = User.builder().nickname("편집 테스트").email(UUID.randomUUID() + "@test.com")
        .gender(Gender.FEMALE).birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build();
    entityManager.persist(user);
    return user;
  }
}
