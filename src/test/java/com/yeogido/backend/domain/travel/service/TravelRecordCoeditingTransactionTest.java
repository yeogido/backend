package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import java.util.concurrent.CountDownLatch;
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

  /** 동일 버전의 동시 요청 중 성공한 요청만 파일을 이동하고 사진을 교체하는지 검증합니다. */
  @Test
  void concurrentSameVersionHasOneWinnerAndPreservesWinnersPhotos() throws Exception {
    CyclicBarrier startTogether = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> {
        startTogether.await(10, TimeUnit.SECONDS);
        return save(ownerId, "생성자 변경");
      });
      var second = pool.submit(() -> {
        startTogether.await(10, TimeUnit.SECONDS);
        return save(participantId, "참여자 변경");
      });
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("OK", "TRAVEL_RECORD4091");
    }
    verify(fileService, times(1)).moveToDirectory(anyString(), any());
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

  /** 편집이 버전을 확보한 뒤 들어온 나가기는 편집 완료를 기다리고 참여 관계만 제거합니다. */
  @Test
  void leaveDuringReservedEditKeepsSavedContentAndAdvancesVersion() throws Exception {
    CountDownLatch editing = new CountDownLatch(1);
    CountDownLatch finishEdit = new CountDownLatch(1);
    CountDownLatch leaving = new CountDownLatch(1);
    when(fileService.moveToDirectory(anyString(), any())).thenAnswer(call -> {
      editing.countDown();
      if (!finishEdit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("편집 대기 시간 초과");
      return call.getArgument(0);
    });
    try (var pool = Executors.newFixedThreadPool(2)) {
      var edit = pool.submit(() -> save(participantId, "저장된 사진"));
      assertThat(editing.await(10, TimeUnit.SECONDS)).isTrue();
      var leave = pool.submit(() -> {
        leaving.countDown();
        service.leaveTravelRecord(albumId, participantId);
        return "OK";
      });
      assertThat(leaving.await(10, TimeUnit.SECONDS)).isTrue();
      finishEdit.countDown();
      assertThat(edit.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
      assertThat(leave.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
    } finally {
      finishEdit.countDown();
    }
    var detail = service.getTravelRecord(ownerId, albumId);
    assertThat(detail.version()).isEqualTo(2L);
    assertThat(detail.title()).isEqualTo("저장된 사진");
    assertThat(detail.participants()).isEmpty();
    assertThat(detail.images().getFirst().imageKey()).isEqualTo("travel-records/저장된 사진.jpg");
    assertThatThrownBy(() -> service.getTravelRecord(participantId, albumId)).isInstanceOf(GeneralException.class);
  }

  /** 편집 중 전체 삭제가 요청되어도 새 사진을 포함해 해당 앨범 데이터가 전부 정리됩니다. */
  @Test
  void deleteDuringReservedEditCleansNewPhotosAndMembership() throws Exception {
    CountDownLatch editing = new CountDownLatch(1);
    CountDownLatch finishEdit = new CountDownLatch(1);
    when(fileService.moveToDirectory(anyString(), any())).thenAnswer(call -> {
      editing.countDown();
      if (!finishEdit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("편집 대기 시간 초과");
      return call.getArgument(0);
    });
    try (var pool = Executors.newFixedThreadPool(2)) {
      var edit = pool.submit(() -> save(participantId, "삭제 전 저장"));
      assertThat(editing.await(10, TimeUnit.SECONDS)).isTrue();
      var delete = pool.submit(() -> {
        service.deleteTravelRecord(albumId, ownerId);
        return "OK";
      });
      finishEdit.countDown();
      assertThat(edit.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
      assertThat(delete.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
    } finally {
      finishEdit.countDown();
    }
    assertAlbumRemoved();
  }

  /** 중복 나가기는 한 요청만 성공하고 나머지는 현재 참여자가 아니므로 403이 됩니다. */
  @Test
  void simultaneousDuplicateLeavesRemoveOnlyOneMembership() throws Exception {
    CyclicBarrier start = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> { start.await(10, TimeUnit.SECONDS); return leave(); });
      var second = pool.submit(() -> { start.await(10, TimeUnit.SECONDS); return leave(); });
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("OK", "COMMON4031");
    }
    var detail = service.getTravelRecord(ownerId, albumId);
    assertThat(detail.version()).isEqualTo(1L);
    assertThat(detail.images()).hasSize(1);
    assertThat(detail.participants()).isEmpty();
  }

  /** 생성자 삭제와 참여자 나가기가 동시에 요청되어도 고아 사진·참여 행이 남지 않습니다. */
  @Test
  void simultaneousDeleteAndLeaveLeaveNoOrphans() throws Exception {
    CyclicBarrier start = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var delete = pool.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        service.deleteTravelRecord(albumId, ownerId);
        return "OK";
      });
      var leave = pool.submit(() -> { start.await(10, TimeUnit.SECONDS); return leave(); });
      assertThat(delete.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
      assertThat(leave.get(20, TimeUnit.SECONDS)).isIn("OK", "TRAVEL_RECORD4041");
    }
    assertAlbumRemoved();
  }

  /** 나가기의 정상 응답과 명세상 실패 코드만 비교하고 예상 밖 예외는 테스트를 실패시킵니다. */
  private String leave() {
    try {
      service.leaveTravelRecord(albumId, participantId);
      return "OK";
    } catch (GeneralException e) {
      return e.getErrorCode().getCode();
    }
  }

  /** 별도 트랜잭션으로 실제 커밋된 삭제 결과를 검증합니다. */
  private void assertAlbumRemoved() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      assertThat(entityManager.find(TravelRecord.class, albumId)).isNull();
      assertThat(entityManager.createQuery("select count(p) from TravelRecordPhoto p where p.travelRecord.id = :id", Long.class)
          .setParameter("id", albumId).getSingleResult()).isZero();
      assertThat(entityManager.createQuery("select count(m) from TravelRecordMember m where m.travelRecord.id = :id", Long.class)
          .setParameter("id", albumId).getSingleResult()).isZero();
    });
  }

  /** 서로 다른 스레드의 서비스 호출 결과에서 충돌 코드와 성공을 비교합니다. */
  private String save(Long userId, String title) {
    try {
      service.updateTravelRecord(albumId, userId, request(title, null));
      return "OK";
    } catch (GeneralException e) {
      return e.getErrorCode().getCode();
    }
  }

  @Test
  void fileMoveFailureRollsBackReservationAndAllowsOriginalVersionRetry() {
    when(fileService.moveToDirectory(anyString(), any())).thenThrow(new IllegalStateException("파일 이동 실패"));
    assertThatThrownBy(() -> service.updateTravelRecord(albumId, ownerId, request("실패", null)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.getTravelRecord(ownerId, albumId).version()).isZero();
    org.mockito.Mockito.doAnswer(call -> call.getArgument(0)).when(fileService).moveToDirectory(anyString(), any());
    assertThat(service.updateTravelRecord(albumId, ownerId, request("재시도", null)).version()).isEqualTo(1L);
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
