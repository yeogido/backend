package com.yeogido.backend.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.global.config.JpaConfig;
import com.yeogido.backend.global.exception.ErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
    "spring.datasource.url=jdbc:h2:mem:travel-visibility-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
class TravelRecordVisibilityIntegrationTest {

  @Autowired private TravelRecordService service;
  @Autowired private TestEntityManager entityManager;
  @MockitoBean private FileService fileService;
  @MockitoBean private S3Service s3Service;

  private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
  private Region region;
  private User viewer;
  private User owner;
  private User third;

  @BeforeEach
  void setUp() {
    region = entityManager.persist(Region.builder().name("부산").type(RegionType.REGION).build());
    viewer = user("조회자");
    owner = user("생성자");
    third = user("다른 참여자");
    when(s3Service.getImageUrl(anyString())).thenAnswer(call -> "https://example.com/" + call.getArgument(0));
    when(fileService.moveToDirectory(anyString(), any())).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void omittedVisibilityCreatesPrivateRecordWithVersionZeroAndPreservesExistingContent() throws Exception {
    var request = createRequest(null);
    assertThat(request.visibility()).isEqualTo("PRIVATE");

    var response = service.createTravelRecord(viewer.getId(), request);
    flushAndClear();
    TravelRecord stored = entityManager.find(TravelRecord.class, response.travelRecordId());

    assertThat(response.visibility()).isEqualTo(TravelRecordVisibility.PRIVATE);
    assertThat(response.version()).isZero();
    assertThat(stored.getVisibility()).isEqualTo(TravelRecordVisibility.PRIVATE);
    assertThat(stored.getFolderTheme()).isEqualTo(FolderTheme.BASIC);
    assertThat(service.getTravelRecord(viewer.getId(), stored.getId()).images()).hasSize(1);
  }

  @ParameterizedTest
  @EnumSource(TravelRecordVisibility.class)
  void storesRequestedVisibilityWithoutCreatingMembersOrInvitations(TravelRecordVisibility visibility) throws Exception {
    var response = service.createTravelRecord(viewer.getId(), createRequest('"' + visibility.name() + '"'));
    flushAndClear();

    assertThat(response.visibility()).isEqualTo(visibility);
    assertThat(response.version()).isZero();
    var detail = service.getTravelRecord(viewer.getId(), response.travelRecordId());
    assertThat(detail.visibility()).isEqualTo(visibility);
    assertThat(detail.owner().userId()).isEqualTo(viewer.getId());
    assertThat(detail.participants()).isEmpty();
    assertThat(entityManager.getEntityManager().createQuery("select count(m) from TravelRecordMember m", Long.class)
        .getSingleResult()).isZero();
    assertThat(entityManager.getEntityManager().createQuery("select count(i) from TravelRecordInvitation i", Long.class)
        .getSingleResult()).isZero();
    assertError(() -> service.getTravelRecord(owner.getId(), response.travelRecordId()),
        visibility == TravelRecordVisibility.PUBLIC ? null : GeneralErrorCode.FORBIDDEN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "\"\"", "\"ALL\"", "\"public\"", "\" PUBLIC \""})
  void rejectsInvalidCreateVisibilityBeforeMovingFiles(String value) throws Exception {
    var request = createRequest(value);
    assertError(() -> service.createTravelRecord(viewer.getId(), request), TravelRecordErrorCode.INVALID_VISIBILITY);
    verify(fileService, never()).moveToDirectory(anyString(), any());
  }

  @Test
  void ownCollectionIncludesOwnedAndCurrentSharedRecordsOnly() {
    TravelRecord ownPrivate = record(viewer, TravelRecordVisibility.PRIVATE, 2026);
    TravelRecord ownPublic = record(viewer, TravelRecordVisibility.PUBLIC, 2026);
    TravelRecord shared = record(owner, TravelRecordVisibility.FRIENDS, 2026);
    member(shared, viewer);
    member(shared, third);
    TravelRecord pending = record(owner, TravelRecordVisibility.FRIENDS, 2026);
    invite(pending, TravelRecordInvitationStatus.PENDING);
    TravelRecord declined = record(owner, TravelRecordVisibility.FRIENDS, 2026);
    invite(declined, TravelRecordInvitationStatus.DECLINED);
    TravelRecord left = record(owner, TravelRecordVisibility.FRIENDS, 2026);
    invite(left, TravelRecordInvitationStatus.ACCEPTED); // 수락 이력만 있고 현재 참여 행은 없는 경우
    record(owner, TravelRecordVisibility.PUBLIC, 2026);
    TravelRecord privateWithStaleMember = record(owner, TravelRecordVisibility.PRIVATE, 2026);
    member(privateWithStaleMember, viewer);
    flushAndClear();

    var result = list(null, null, 2026, null, 20);
    assertThat(result.getItems()).extracting(TravelRecordResDTO.TravelRecordSummary::travelRecordId)
        .containsExactly(shared.getId(), ownPublic.getId(), ownPrivate.getId());
    assertThat(result.isHasNext()).isFalse();
    assertThat(result.getCursorValue()).isNull();
    assertThat(result.getItems().getFirst().participants())
        .extracting(TravelRecordResDTO.UserProfile::userId).containsExactly(viewer.getId(), third.getId());
    assertThat(list(viewer.getId(), null, 2026, null, 20).getItems()).isEqualTo(result.getItems());
    assertError(() -> service.getTravelRecord(viewer.getId(), pending.getId()), GeneralErrorCode.FORBIDDEN);
    assertError(() -> service.getTravelRecord(viewer.getId(), declined.getId()), GeneralErrorCode.FORBIDDEN);
    assertError(() -> service.getTravelRecord(viewer.getId(), left.getId()), GeneralErrorCode.FORBIDDEN);
    assertError(() -> service.getTravelRecord(viewer.getId(), privateWithStaleMember.getId()), GeneralErrorCode.FORBIDDEN);
  }

  @Test
  void otherCollectionContainsOnlyTargetOwnedPublicOrMutuallySharedAlbums() {
    TravelRecord publicRecord = record(owner, TravelRecordVisibility.PUBLIC, 2026);
    TravelRecord shared = record(owner, TravelRecordVisibility.FRIENDS, 2026);
    member(shared, viewer);
    record(owner, TravelRecordVisibility.PRIVATE, 2026);
    record(owner, TravelRecordVisibility.FRIENDS, 2026);
    TravelRecord thirdOwned = record(third, TravelRecordVisibility.FRIENDS, 2026);
    member(thirdOwned, owner);
    member(thirdOwned, viewer);
    TravelRecord viewerOwned = record(viewer, TravelRecordVisibility.FRIENDS, 2026);
    member(viewerOwned, owner);
    flushAndClear();

    assertThat(list(owner.getId(), null, 2026, null, 20).getItems())
        .extracting(TravelRecordResDTO.TravelRecordSummary::travelRecordId)
        .containsExactly(shared.getId(), publicRecord.getId());
    assertThat(list(owner.getId(), "PRIVATE", 2026, null, 20).getItems()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(TravelRecordVisibility.class)
  void visibilityFiltersDoNotExpandReadAccessAndYearsUseIdenticalConditions(TravelRecordVisibility visibility) {
    record(viewer, TravelRecordVisibility.PRIVATE, 2022);
    record(viewer, TravelRecordVisibility.PUBLIC, 2023);
    TravelRecord shared = record(owner, TravelRecordVisibility.FRIENDS, 2024);
    member(shared, viewer);
    record(owner, TravelRecordVisibility.PRIVATE, 2020);
    record(owner, TravelRecordVisibility.FRIENDS, 2021);
    record(owner, TravelRecordVisibility.PUBLIC, 2025);
    flushAndClear();
    int ownYear = switch (visibility) { case PRIVATE -> 2022; case PUBLIC -> 2023; case FRIENDS -> 2024; };
    assertThat(years(null, visibility.name())).containsExactly(ownYear);
    assertThat(list(null, visibility.name(), ownYear, null, 10).getItems()).hasSize(1);
    assertThat(years(owner.getId(), visibility.name())).containsExactlyElementsOf(switch (visibility) {
      case PRIVATE -> List.of(); case PUBLIC -> List.of(2025); case FRIENDS -> List.of(2024);
    });
  }

  @Test
  void yearsAreDistinctDescendingAndBasedOnEndDate() {
    TravelRecord crossing = entityManager.persist(TravelRecord.builder().user(viewer).region(region).title("연말 여행")
        .startDate(LocalDate.of(2025, 12, 31)).endDate(LocalDate.of(2026, 1, 1))
        .coverImageKey("travel-records/crossing.jpg").build());
    record(viewer, TravelRecordVisibility.PRIVATE, 2026);
    record(viewer, TravelRecordVisibility.PRIVATE, 2024);
    TravelRecord hiddenYear = record(owner, TravelRecordVisibility.FRIENDS, 2020);
    invite(hiddenYear, TravelRecordInvitationStatus.PENDING);
    flushAndClear();

    assertThat(years(null, null)).containsExactly(2026, 2024);
    assertThat(list(null, null, 2025, null, 10).getItems()).isEmpty();
    assertThat(list(null, null, 2026, null, 10).getItems())
        .extracting(TravelRecordResDTO.TravelRecordSummary::travelRecordId).contains(crossing.getId());
    assertThat(years(third.getId(), null)).isEmpty();
  }

  @Test
  void cursorPaginationOrdersByEndDateThenIdAcrossDifferentCreators() {
    TravelRecord first = record(viewer, TravelRecordVisibility.PRIVATE, 2026);
    TravelRecord second = record(owner, TravelRecordVisibility.FRIENDS, 2026);
    member(second, viewer);
    TravelRecord thirdRecord = record(viewer, TravelRecordVisibility.PUBLIC, 2026);
    flushAndClear();

    List<Long> ids = new ArrayList<>();
    Long cursor = null;
    for (int i = 0; i < 3; i++) {
      var page = list(null, null, 2026, cursor, 1);
      assertThat(page.getItems()).hasSize(1);
      assertThat(page.isHasNext()).isEqualTo(i < 2);
      ids.add(page.getItems().getFirst().travelRecordId());
      cursor = page.getCursorId();
    }
    assertThat(ids).containsExactly(thirdRecord.getId(), second.getId(), first.getId());
    var empty = list(null, null, 2026, cursor, 1);
    assertThat(empty.getItems()).isEmpty();
    assertThat(empty.getCursorId()).isNull();
    assertThat(empty.isHasNext()).isFalse();
  }

  @Test
  void cursorMustBelongToCurrentTargetVisibilityYearAndReadScope() {
    TravelRecord own = record(viewer, TravelRecordVisibility.PRIVATE, 2026);
    TravelRecord otherPublic = record(owner, TravelRecordVisibility.PUBLIC, 2026);
    TravelRecord forbidden = record(owner, TravelRecordVisibility.FRIENDS, 2026);
    TravelRecord wrongYear = record(viewer, TravelRecordVisibility.PRIVATE, 2025);
    flushAndClear();

    assertError(() -> list(null, null, 2026, otherPublic.getId(), 10), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
    assertError(() -> list(owner.getId(), null, 2026, own.getId(), 10), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
    assertError(() -> list(null, null, 2026, forbidden.getId(), 10), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
    assertError(() -> list(null, null, 2026, wrongYear.getId(), 10), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
    assertError(() -> list(null, "PUBLIC", 2026, own.getId(), 10), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
    assertError(() -> list(null, null, 2026, Long.MAX_VALUE, 10), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
  }

  @Test
  void cursorUsesEndDateBeforeIdAndLosingMembershipInvalidatesSharedCursor() {
    TravelRecord latest = record(owner, TravelRecordVisibility.FRIENDS, 2026);
    TravelRecordMember joined = entityManager.persist(TravelRecordMember.builder().travelRecord(latest).user(viewer).build());
    TravelRecord olderWithLargerId = record(viewer, TravelRecordVisibility.PRIVATE, 2026);
    olderWithLargerId.update(region, "이전 여행", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3),
        olderWithLargerId.getCoverImageKey(), FolderTheme.BASIC);
    flushAndClear();

    var first = list(null, null, 2026, null, 1);
    assertThat(first.getItems().getFirst().travelRecordId()).isEqualTo(latest.getId());
    assertThat(list(null, null, 2026, first.getCursorId(), 1).getItems())
        .extracting(TravelRecordResDTO.TravelRecordSummary::travelRecordId).containsExactly(olderWithLargerId.getId());

    entityManager.remove(entityManager.find(TravelRecordMember.class, joined.getId()));
    flushAndClear();
    assertError(() -> list(null, null, 2026, first.getCursorId(), 1), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
    assertError(() -> service.getTravelRecord(viewer.getId(), latest.getId()), GeneralErrorCode.FORBIDDEN);
  }

  @Test
  void privateAlbumAndItsPhotoUrlAreNotResolvedForAnotherUser() {
    TravelRecord privateRecord = record(owner, TravelRecordVisibility.PRIVATE, 2026);
    photo(privateRecord, 1);
    flushAndClear();

    assertError(() -> service.getTravelRecord(viewer.getId(), privateRecord.getId()), GeneralErrorCode.FORBIDDEN);
    assertThat(list(owner.getId(), "PRIVATE", 2026, null, 10).getItems()).isEmpty();
    verify(s3Service, never()).getImageUrl(anyString());
  }

  @Test
  void detailReturnsCurrentMembersPermissionsAndOrderedPhotosAndStickers() {
    TravelRecord shared = record(owner, TravelRecordVisibility.FRIENDS, 2026);
    member(shared, viewer);
    member(shared, third);
    User withdrawn = user("탈퇴자");
    member(shared, withdrawn);
    withdrawn.withdraw();
    photo(shared, 3);
    photo(shared, 1);
    photo(shared, 2);
    Sticker sticker = entityManager.persist(Sticker.builder().name("하트").imageKey("stickers/heart.png")
        .stickerType(StickerType.DEFAULT).category(StickerCategory.values()[0]).build());
    entityManager.persist(TravelRecordSticker.builder().travelRecord(shared).sticker(sticker)
        .positionX(1.0).positionY(2.0).rotation(0.0).scale(1.0).zIndex(1).build());
    flushAndClear();

    var response = service.getTravelRecord(viewer.getId(), shared.getId());
    assertThat(response.owner().userId()).isEqualTo(owner.getId());
    assertThat(response.participants()).extracting(TravelRecordResDTO.UserProfile::userId)
        .containsExactly(viewer.getId(), third.getId());
    assertThat(response.version()).isZero();
    assertThat(response.permissions()).isEqualTo(new TravelRecordResDTO.Permissions(true, false, true, true, false));
    assertThat(service.getTravelRecord(owner.getId(), shared.getId()).permissions())
        .isEqualTo(new TravelRecordResDTO.Permissions(true, true, false, true, false));
    assertThat(response.images()).extracting(TravelRecordResDTO.ImageResponse::imageOrder).containsExactly(1, 2, 3);
    assertThat(response.stickers()).hasSize(1);
    assertThat(response.stickers().getFirst().imageUrl()).isEqualTo("https://example.com/stickers/heart.png");
    var summary = list(null, null, 2026, null, 10).getItems().getFirst();
    assertThat(summary.previewImages()).extracting(TravelRecordResDTO.PreviewImage::imageOrder).containsExactly(1, 2);
    assertThat(summary.stickers()).isEqualTo(response.stickers());
  }

  @Test
  void publicViewerCanOnlyReadAndPrivateOwnerCanEditDelete() {
    TravelRecord publicRecord = record(owner, TravelRecordVisibility.PUBLIC, 2026);
    TravelRecord privateRecord = record(viewer, TravelRecordVisibility.PRIVATE, 2026);
    flushAndClear();

    assertThat(service.getTravelRecord(viewer.getId(), publicRecord.getId()).permissions())
        .isEqualTo(new TravelRecordResDTO.Permissions(false, false, false, false, true));
    assertThat(service.getTravelRecord(viewer.getId(), privateRecord.getId()).permissions())
        .isEqualTo(new TravelRecordResDTO.Permissions(true, true, false, false, false));
    assertError(() -> service.updateTravelRecord(publicRecord.getId(), viewer.getId(), null), GeneralErrorCode.FORBIDDEN);
    assertError(() -> service.deleteTravelRecord(publicRecord.getId(), viewer.getId()), GeneralErrorCode.FORBIDDEN);
  }

  @Test
  void withdrawnOwnerIsExcludedAndCannotBeUsedAsAnotherUsersCollection() {
    TravelRecord publicRecord = record(owner, TravelRecordVisibility.PUBLIC, 2026);
    TravelRecord shared = record(owner, TravelRecordVisibility.FRIENDS, 2026);
    member(shared, viewer);
    owner.withdraw();
    flushAndClear();

    assertThat(list(null, null, 2026, null, 10).getItems()).isEmpty();
    assertThat(years(null, null)).isEmpty();
    assertError(() -> list(owner.getId(), null, 2026, null, 10), UserErrorCode.USER_NOT_FOUND);
    assertError(() -> years(owner.getId(), null), UserErrorCode.USER_NOT_FOUND);
    assertError(() -> service.getTravelRecord(viewer.getId(), publicRecord.getId()), TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND);
  }

  @Test
  void nonExistingTargetReturnsUserNotFound() {
    assertError(() -> list(Long.MAX_VALUE, null, 2026, null, 10), UserErrorCode.USER_NOT_FOUND);
    assertError(() -> years(Long.MAX_VALUE, null), UserErrorCode.USER_NOT_FOUND);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"ALL", "public", " PUBLIC "})
  void nullFilterMeansAllAndOtherInvalidFiltersAreRejected(String visibility) {
    if (visibility == null) {
      assertThat(list(null, null, 2026, null, 10).getItems()).isEmpty();
      assertThat(years(null, null)).isEmpty();
    } else {
      assertError(() -> list(null, visibility, 2026, null, 10), TravelRecordErrorCode.INVALID_VISIBILITY);
      assertError(() -> years(null, visibility), TravelRecordErrorCode.INVALID_VISIBILITY);
    }
  }

  @Test
  void invalidPageValuesFailBeforeDatabasePagination() {
    assertError(() -> list(null, null, 2026, -1L, 10), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
    assertError(() -> list(null, null, 2026, null, 0), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
    assertError(() -> list(null, null, 2026, null, Integer.MAX_VALUE), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
    assertError(() -> list(null, null, 10000, null, 10), TravelRecordErrorCode.INVALID_PAGE_REQUEST);
  }

  @Test
  void listLoadsProfilesPhotosAndStickersInFourQueriesRegardlessOfAlbumCount() {
    for (int i = 0; i < 5; i++) {
      TravelRecord record = record(owner, TravelRecordVisibility.FRIENDS, 2026);
      member(record, viewer);
      photo(record, 1);
    }
    flushAndClear();
    var statistics = entityManager.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class)
        .getStatistics();
    statistics.clear();

    assertThat(list(null, null, 2026, null, 10).getItems()).hasSize(5);
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
  }

  /** JSON을 통해 생략과 명시적 null을 구분하고 기존 필수 입력을 함께 제공합니다. */
  private TravelRecordReqDTO.CreateRequest createRequest(String visibilityJson) throws Exception {
    return mapper.readValue("""
        {"title":"부산 여행","regionId":%d,"startDate":"2026-05-22","endDate":"2026-05-24",
         "images":[{"imageKey":"travel-records/test.jpg","imageOrder":1}]%s}
        """.formatted(region.getId(), visibilityJson == null ? "" : ",\"visibility\":" + visibilityJson),
        TravelRecordReqDTO.CreateRequest.class);
  }

  private com.yeogido.backend.global.common.response.CursorResponse<TravelRecordResDTO.TravelRecordSummary> list(
      Long targetId, String visibility, int year, Long cursor, int size) {
    return service.getMyTravelRecords(viewer.getId(), new TravelRecordReqDTO.ListRequest(cursor, size, year, visibility, targetId));
  }

  private List<Integer> years(Long targetId, String visibility) {
    return service.getMyTravelRecordYears(viewer.getId(), new TravelRecordReqDTO.YearListRequest(targetId, visibility)).years();
  }

  private User user(String nickname) {
    return entityManager.persist(User.builder().nickname(nickname).email(UUID.randomUUID() + "@test.com")
        .gender(Gender.FEMALE).birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build());
  }

  /** 같은 종료일로 저장하여 ID를 보조 정렬로 사용하는 페이지 경계를 검증합니다. */
  private TravelRecord record(User creator, TravelRecordVisibility visibility, int year) {
    return entityManager.persist(TravelRecord.builder().user(creator).region(region).title("여행")
        .startDate(LocalDate.of(year, 5, 1)).endDate(LocalDate.of(year, 5, 3))
        .coverImageKey("travel-records/cover.jpg").folderTheme(FolderTheme.BASIC).visibility(visibility).build());
  }

  private void member(TravelRecord record, User user) {
    entityManager.persist(TravelRecordMember.builder().travelRecord(record).user(user).build());
  }

  private void invite(TravelRecord record, TravelRecordInvitationStatus status) {
    entityManager.persist(TravelRecordInvitation.builder().travelRecord(record).inviter(owner).invitee(viewer).status(status).build());
  }

  private void photo(TravelRecord record, int order) {
    entityManager.persist(TravelRecordPhoto.builder().travelRecord(record).imageKey("travel-records/" + order + ".jpg")
        .imageOrder(order).build());
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private void assertError(Runnable action, ErrorCode code) {
    if (code == null) {
      action.run();
      return;
    }
    assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
        exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
  }
}
