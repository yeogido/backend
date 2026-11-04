package com.yeogido.backend.domain.content.repository;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentPublicationStatus;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.global.config.JpaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.service.ContentService;
import com.yeogido.backend.domain.content.service.ContentServiceImpl;
import com.yeogido.backend.domain.content.service.ContentExternalLinkService;
import com.yeogido.backend.domain.user.service.AdminAuthorizationService;
import com.yeogido.backend.domain.place.service.PlaceService;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentSort;
import com.yeogido.backend.domain.content.entity.ContentLike;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.config.QueryDslConfig;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties={
    "spring.flyway.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:calendar410;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=ContentListDateIntegrationTest.Config.class)
class ContentListDateIntegrationTest {
  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.yeogido.backend.domain")
  @EnableJpaRepositories(basePackages="com.yeogido.backend.domain")
  @Import({JpaConfig.class, QueryDslConfig.class, ContentServiceImpl.class})
  static class Config {}

  @Autowired TestEntityManager em;
  @Autowired ContentService service;
  @MockitoBean AdminAuthorizationService adminAuthorizationService;
  @MockitoBean ContentExternalLinkService contentExternalLinkService;
  @MockitoBean PlaceService placeService;
  @MockitoBean FileService fileService;
  @MockitoBean S3Service s3Service;
  Place place;

  @BeforeEach
  void setUp() {
    Region region = em.persist(Region.builder().name("서울").fullName("서울")
        .type(RegionType.REGION).build());
    place = em.persist(Place.builder().region(region).source(PlaceSource.KAKAO)
        .externalPlaceId("calendar-test").name("행사장")
        .latitude(BigDecimal.ZERO).longitude(BigDecimal.ZERO).build());
  }

  @Test
  void includesAnyOverlapAndBothBoundariesInStableOrder() {
    save("전체 포함", "2026-09-01", "2026-11-01", ContentPublicationStatus.PUBLISHED);
    save("월초 종료", "2026-09-30", "2026-10-01", ContentPublicationStatus.PUBLISHED);
    save("하루 행사 1", "2026-10-15", "2026-10-15", ContentPublicationStatus.PUBLISHED);
    save("하루 행사 2", "2026-10-15", "2026-10-15", ContentPublicationStatus.PUBLISHED);
    save("월말 시작", "2026-10-31", "2026-11-02", ContentPublicationStatus.PUBLISHED);
    save("이전 월", "2026-09-01", "2026-09-30", ContentPublicationStatus.PUBLISHED);
    save("다음 월", "2026-11-01", "2026-11-02", ContentPublicationStatus.PUBLISHED);
    save("미게시", "2026-10-01", "2026-10-31", ContentPublicationStatus.PENDING);
    save("시작일 없음", null, "2026-10-31", ContentPublicationStatus.PUBLISHED);
    save("종료일 없음", "2026-10-01", null, ContentPublicationStatus.PUBLISHED);
    em.flush(); em.clear();

    assertThat(findCalendarContents(
        LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)))
        .extracting(ContentResDTO.ContentInfo::title)
        .containsExactly("전체 포함","월초 종료","하루 행사 1","하루 행사 2","월말 시작");
  }

  @Test
  void includesLeapDayAndYearBoundary() {
    save("윤일", "2028-02-29", "2028-03-01", ContentPublicationStatus.PUBLISHED);
    save("연도 경계", "2027-12-31", "2028-01-01", ContentPublicationStatus.PUBLISHED);
    em.flush(); em.clear();
    assertThat(findCalendarContents(
        LocalDate.of(2028,2,1),LocalDate.of(2028,2,29)))
        .extracting(ContentResDTO.ContentInfo::title).containsExactly("윤일");
    assertThat(findCalendarContents(
        LocalDate.of(2028,1,1),LocalDate.of(2028,1,31)))
        .extracting(ContentResDTO.ContentInfo::title).containsExactly("연도 경계");
  }

  @Test
  void returnsEmptyWhenNoEventsOverlap() {
    save("다른 달", "2026-09-01", "2026-09-30", ContentPublicationStatus.PUBLISHED);
    em.flush(); em.clear();
    assertThat(findCalendarContents(
        LocalDate.of(2026,10,1),LocalDate.of(2026,10,31))).isEmpty();
  }

  @Test
  void countsMatchingEventsIndependentlyOfThePageCursor() {
    save("첫 행사", "2026-10-01", "2026-10-10", ContentPublicationStatus.PUBLISHED);
    save("둘째 행사", "2026-10-01", "2026-10-20", ContentPublicationStatus.PUBLISHED);
    save("미게시", "2026-10-01", "2026-10-15", ContentPublicationStatus.PENDING);
    save("다른 달", "2026-09-01", "2026-09-30", ContentPublicationStatus.PUBLISHED);
    em.flush();
    em.clear();

    LocalDate start = LocalDate.of(2026, 10, 1);
    LocalDate end = LocalDate.of(2026, 10, 31);
    var first = service.getContents(new ContentReqDTO.ContentListReq(null, null, null, null, null,
        ContentSort.DEADLINE, null, null, null, null, 1, start, end), null);
    var second = service.getContents(new ContentReqDTO.ContentListReq(null, null, null, null, null,
        ContentSort.DEADLINE, null, null, first.getCursorValue().toString(), first.getCursorId(), 1, start, end), null);
    var empty = service.getContents(new ContentReqDTO.ContentListReq(null, null, null, null, null,
        ContentSort.DEADLINE, null, null, null, null, 1, end.plusDays(1), end.plusMonths(1)), null);

    assertThat(first.getItems()).hasSize(1);
    assertThat(first.getTotalCount()).isEqualTo(2);
    assertThat(second.getItems()).hasSize(1);
    assertThat(second.getTotalCount()).isEqualTo(2);
    assertThat(empty.getTotalCount()).isZero();
  }

  private Content save(String title,String start,String end,ContentPublicationStatus status) {
    return em.persist(Content.builder().place(place).source(ContentSource.ADMIN).title(title)
        .category(ContentCategory.FESTIVAL).startDate(start==null ? null : LocalDate.parse(start))
        .endDate(end==null ? null : LocalDate.parse(end)).publicationStatus(status).build());
  }
  @Test
  void dayQueryIncludesEventsStartingOrEndingThatDay() {
    save("당일 종료", "2026-10-01", "2026-10-15", ContentPublicationStatus.PUBLISHED);
    save("당일 시작", "2026-10-15", "2026-10-20", ContentPublicationStatus.PUBLISHED);
    save("당일만", "2026-10-15", "2026-10-15", ContentPublicationStatus.PUBLISHED);
    save("전날 종료", "2026-10-01", "2026-10-14", ContentPublicationStatus.PUBLISHED);
    save("다음날 시작", "2026-10-16", "2026-10-20", ContentPublicationStatus.PUBLISHED);
    em.flush(); em.clear();
    LocalDate date = LocalDate.of(2026,10,15);
    assertThat(findCalendarContents(date,date))
        .extracting(ContentResDTO.ContentInfo::title).containsExactly("당일 종료","당일 시작","당일만");
  }

  @Test
  void rangeQueryIncludesPartialAndContainingOverlaps() {
    save("선택 기간 전체 포함", "2026-09-01", "2026-11-01", ContentPublicationStatus.PUBLISHED);
    save("첫날 종료", "2026-10-01", "2026-10-10", ContentPublicationStatus.PUBLISHED);
    save("마지막날 시작", "2026-10-20", "2026-10-31", ContentPublicationStatus.PUBLISHED);
    save("이전 기간", "2026-10-01", "2026-10-09", ContentPublicationStatus.PUBLISHED);
    save("다음 기간", "2026-10-21", "2026-10-31", ContentPublicationStatus.PUBLISHED);
    em.flush(); em.clear();
    assertThat(findCalendarContents(
        LocalDate.of(2026,10,10),LocalDate.of(2026,10,20)))
        .extracting(ContentResDTO.ContentInfo::title).containsExactly("선택 기간 전체 포함","첫날 종료","마지막날 시작");
  }

  private List<ContentResDTO.ContentInfo> findCalendarContents(LocalDate start, LocalDate end) {
    return service.getContents(new ContentReqDTO.ContentListReq(null,null,null,null,null,null,null,null,null,null,100,start,end),null).getItems();
  }

  private List<ContentResDTO.ContentInfo> query(ContentCategory category, ContentSort sort, Double latitude, Double longitude) {
    return service.getContents(new ContentReqDTO.ContentListReq(null,category,null,null,null,sort,
        latitude,longitude,null,null,100,LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)),null).getItems();
  }

  @Test
  void recommendsPositivePrioritiesFirstThenUnrecommendedWithIdTies() {
    save("미추천", "2026-10-01","2026-10-31",ContentPublicationStatus.PUBLISHED);
    save("추천 2", "2026-10-01","2026-10-31",ContentPublicationStatus.PUBLISHED).updateRecommendPriority(2);
    save("추천 1 첫번째", "2026-10-01","2026-10-31",ContentPublicationStatus.PUBLISHED).updateRecommendPriority(1);
    save("추천 1 두번째", "2026-10-01","2026-10-31",ContentPublicationStatus.PUBLISHED).updateRecommendPriority(1);
    em.flush(); em.clear();
    assertThat(query(null,null,null,null)).extracting(ContentResDTO.ContentInfo::title)
        .containsExactly("추천 1 첫번째","추천 1 두번째","추천 2","미추천");
  }

  @Test
  void combinesCategoryWithDateAndPublicationFiltersAndSortsByDeadline() {
    save("늦게 종료", "2026-10-01","2026-11-01",ContentPublicationStatus.PUBLISHED);
    save("먼저 종료", "2026-10-01","2026-10-10",ContentPublicationStatus.PUBLISHED);
    save("먼저 종료 두번째", "2026-10-01","2026-10-10",ContentPublicationStatus.PUBLISHED);
    save("미게시", "2026-10-01","2026-10-15",ContentPublicationStatus.PENDING);
    save("다른 월", "2026-09-01","2026-09-30",ContentPublicationStatus.PUBLISHED);
    em.persist(Content.builder().place(place).source(ContentSource.ADMIN).title("다른 카테고리")
        .category(ContentCategory.EXHIBITION).startDate(LocalDate.of(2026,10,1))
        .endDate(LocalDate.of(2026,10,2)).build());
    em.flush(); em.clear();
    assertThat(query(ContentCategory.FESTIVAL,ContentSort.DEADLINE,null,null)).extracting(ContentResDTO.ContentInfo::title)
        .containsExactly("먼저 종료","먼저 종료 두번째","늦게 종료");
    assertThat(query(ContentCategory.EXHIBITION,ContentSort.RECOMMEND,null,null)).extracting(ContentResDTO.ContentInfo::title)
        .containsExactly("다른 카테고리");
    assertThat(query(ContentCategory.EXPERIENCE,ContentSort.RECOMMEND,null,null)).isEmpty();
  }

  @Test
  void sortsByLikeCountIncludingZeroAndKeepsIdOrderForTies() {
    save("저장 0", "2026-10-01","2026-10-31",ContentPublicationStatus.PUBLISHED);
    Content one = save("저장 1", "2026-10-01","2026-10-31",ContentPublicationStatus.PUBLISHED);
    Content two = save("저장 2", "2026-10-01","2026-10-31",ContentPublicationStatus.PUBLISHED);
    for (int i=0;i<2;i++) {
      User user = em.persist(User.builder().nickname("사용자"+i).email("calendar"+i+"@example.com")
          .gender(Gender.MALE).birthYear("2000").role(UserRole.USER).status(UserStatus.ACTIVE)
          .region(place.getRegion()).build());
      em.persist(ContentLike.builder().content(two).user(user).build());
      if (i==0) em.persist(ContentLike.builder().content(one).user(user).build());
    }
    em.flush(); em.clear();
    assertThat(query(null,ContentSort.LIKE,null,null)).extracting(ContentResDTO.ContentInfo::title)
        .containsExactly("저장 2","저장 1","저장 0");
  }

  @Test
  void sortsByDistanceWithinDateRange() {
    Place far = em.persist(Place.builder().region(place.getRegion()).source(PlaceSource.KAKAO)
        .externalPlaceId("far").name("먼 장소").latitude(new BigDecimal("2"))
        .longitude(BigDecimal.ZERO).build());
    em.persist(Content.builder().place(far).source(ContentSource.ADMIN).title("먼 행사")
        .startDate(LocalDate.of(2026,10,1)).endDate(LocalDate.of(2026,10,31)).build());
    save("가까운 행사", "2026-10-01","2026-10-31",ContentPublicationStatus.PUBLISHED);
    em.flush(); em.clear();
    assertThat(query(null,ContentSort.DISTANCE,0.0,0.0)).extracting(ContentResDTO.ContentInfo::title)
        .containsExactly("가까운 행사","먼 행사");

  }

}
