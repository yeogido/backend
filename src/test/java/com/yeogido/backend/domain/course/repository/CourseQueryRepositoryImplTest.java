package com.yeogido.backend.domain.course.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseLike;
import com.yeogido.backend.domain.course.entity.CourseReview;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseSortType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.config.JpaConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:course-query-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class CourseQueryRepositoryImplTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findCoursesByCursorSortsBySavedCountOnlyWhenSavedSortIsRequested() {
        Region region = persistRegion("Seoul", "37.5665", "126.9780");
        Course first = persistCourse(region, "saved first");
        Course second = persistCourse(region, "saved second");
        Course third = persistCourse(region, "saved third");
        User user1 = persistUser(region, "user1@example.com");
        User user2 = persistUser(region, "user2@example.com");

        persistLike(first, user1);
        persistLike(first, user2);
        persistLike(second, user1);
        persistReview(third, user1);
        flushAndClear();

        List<CourseQueryRepository.CourseListRow> rows = courseRepository.findCoursesByCursor(
                courseListRequest(CourseSortType.SAVED),
                new CourseQueryRepository.CourseLocation(null, null),
                Map.of(),
                10
        );

        assertThat(rows)
                .extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(rows)
                .extracting(CourseQueryRepository.CourseListRow::savedCount)
                .containsExactly(2L, 1L, 0L);
    }

    @Test
    void findCoursesByCursorSortsByReviewCountOnlyWhenReviewSortIsRequested() {
        Region region = persistRegion("Busan", "35.1796", "129.0756");
        Course first = persistCourse(region, "review first");
        Course second = persistCourse(region, "review second");
        Course third = persistCourse(region, "review third");
        User user1 = persistUser(region, "review1@example.com");
        User user2 = persistUser(region, "review2@example.com");

        persistReview(first, user1);
        persistReview(second, user1);
        persistReview(second, user2);
        persistLike(third, user1);
        flushAndClear();

        List<CourseQueryRepository.CourseListRow> rows = courseRepository.findCoursesByCursor(
                courseListRequest(CourseSortType.REVIEW),
                new CourseQueryRepository.CourseLocation(null, null),
                Map.of(),
                10
        );

        assertThat(rows)
                .extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(second.getId(), first.getId(), third.getId());
        assertThat(rows)
                .extracting(CourseQueryRepository.CourseListRow::reviewCount)
                .containsExactly(2L, 1L, 0L);
    }

    @Test
    void findCoursesByCursorSortsByDistanceWithoutCountAggregates() {
        Region nearRegion = persistRegion("Near", "37.5665", "126.9780");
        Region farRegion = persistRegion("Far", "35.1796", "129.0756");
        Course nearCourse = persistCourse(nearRegion, "near course");
        Course farCourse = persistCourse(farRegion, "far course");
        User user = persistUser(nearRegion, "distance@example.com");

        persistLike(farCourse, user);
        persistReview(farCourse, user);
        flushAndClear();

        List<CourseQueryRepository.CourseListRow> rows = courseRepository.findCoursesByCursor(
                courseListRequest(CourseSortType.DISTANCE),
                new CourseQueryRepository.CourseLocation(37.5665, 126.9780),
                Map.of(),
                10
        );

        assertThat(rows)
                .extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(nearCourse.getId(), farCourse.getId());
        assertThat(rows)
                .extracting(CourseQueryRepository.CourseListRow::savedCount)
                .containsOnly(0L);
        assertThat(rows)
                .extracting(CourseQueryRepository.CourseListRow::reviewCount)
                .containsOnly(0L);
    }

    @Test
    void findCoursesByCursorSortsZeroRecommendOrderAfterPositiveOrdersAndBeforeNull() {
        Region region = persistRegion("Recommend", "37.5665", "126.9780");
        Course second = persistCourse(region, "recommend second", 2);
        Course high = persistCourse(region, "recommend high", 1_000_000_000);
        Course zero = persistCourse(region, "recommend zero", 0);
        Course unspecified = persistCourse(region, "recommend unspecified", null);
        Course first = persistCourse(region, "recommend first", 1);
        flushAndClear();

        List<CourseQueryRepository.CourseListRow> rows = courseRepository.findCoursesByCursor(
                courseListRequest(CourseSortType.RECOMMEND),
                new CourseQueryRepository.CourseLocation(null, null),
                Map.of(),
                10
        );

        assertThat(rows)
                .extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(
                        first.getId(),
                        second.getId(),
                        high.getId(),
                        zero.getId(),
                        unspecified.getId()
                );
    }

    @Test
    void findCoursesByCursorKeepsRecommendCursorOrderForZeroAndNullOrders() {
        Region region = persistRegion("Recommend Cursor", "37.5665", "126.9780");
        Course second = persistCourse(region, "cursor second", 2);
        Course high = persistCourse(region, "cursor high", 1_000_000_000);
        Course zero = persistCourse(region, "cursor zero", 0);
        Course unspecified = persistCourse(region, "cursor unspecified", null);
        persistCourse(region, "cursor first", 1);
        flushAndClear();

        List<CourseQueryRepository.CourseListRow> rows = courseRepository.findCoursesByCursor(
                courseListRequest(CourseSortType.RECOMMEND, "2", second.getId()),
                new CourseQueryRepository.CourseLocation(null, null),
                Map.of(),
                10
        );

        assertThat(rows)
                .extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(high.getId(), zero.getId(), unspecified.getId());
    }

    @Test
    void findCoursesByCursorKeepsRecommendCursorOrderWithinNullOrders() {
        Region region = persistRegion("Recommend Null Cursor", "37.5665", "126.9780");
        persistCourse(region, "null cursor first", 1);
        Course olderNull = persistCourse(region, "null cursor older", null);
        Course newerNull = persistCourse(region, "null cursor newer", null);
        flushAndClear();

        List<CourseQueryRepository.CourseListRow> rows = courseRepository.findCoursesByCursor(
                courseListRequest(CourseSortType.RECOMMEND, null, newerNull.getId()),
                new CourseQueryRepository.CourseLocation(null, null),
                Map.of(),
                10
        );

        assertThat(rows)
                .extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(olderNull.getId());
    }

    @Test
    void findCoursesByCursorIncludesCourseTypeAndAuthorUserId() {
        Region region = persistRegion("Local Author", "37.5665", "126.9780");
        User user = persistUser(region, "author@example.com");
        Course course = persistLocalCourse(region, user, "local author course");
        flushAndClear();

        List<CourseQueryRepository.CourseListRow> rows = courseRepository.findCoursesByCursor(
                courseListRequest(CourseType.LOCAL, CourseSortType.LATEST),
                new CourseQueryRepository.CourseLocation(null, null),
                Map.of(),
                10
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).courseId()).isEqualTo(course.getId());
        assertThat(rows.get(0).courseType()).isEqualTo(CourseType.LOCAL);
        assertThat(rows.get(0).authorUserId()).isEqualTo(user.getId());
    }

    @Test
    void findRecommendedCoursesSortsZeroRecommendOrderAfterPositiveOrders() {
        Region region = persistRegion("Recommended Courses", "37.5665", "126.9780");
        Course second = persistCourse(region, "repository second", 2);
        Course high = persistCourse(region, "repository high", 1_000_000_000);
        Course zero = persistCourse(region, "repository zero", 0);
        Course unspecified = persistCourse(region, "repository unspecified", null);
        Course first = persistCourse(region, "repository first", 1);
        flushAndClear();

        List<CourseRepository.CourseRecommendedProjection> rows = courseRepository.findRecommendedCourses(
                PageRequest.of(0, 10)
        );

        assertThat(rows)
                .extracting(CourseRepository.CourseRecommendedProjection::getCourseId)
                .containsExactly(first.getId(), second.getId(), high.getId(), zero.getId())
                .doesNotContain(unspecified.getId());
    }

    @Test
    void naturalLanguageTermsMustAllMatchAndMayMatchDifferentPlacesWithoutDuplicatingCourses() {
        Region region = persistRegion("Busan", "35.1796", "129.0756");
        Course matching = persistCourse(region, "coastal route", 1);
        persistPlaceItem(matching, persistPlace(region, "바다 전망대", "파도와 해변"), 1);
        persistPlaceItem(matching, persistPlace(region, "쉼터", "조용한 산책길"), 2);
        persistPlaceItem(matching, persistPlace(region, "바다 카페", "조용한 휴식"), 3);
        Course partial = persistCourse(region, "partial route", 2);
        persistPlaceItem(partial, persistPlace(region, "바다 놀이터", "활기찬 공간"), 1);
        persistCourse(region, "unrelated route", 3);
        flushAndClear();

        var rows = courseRepository.findCoursesByCursor(courseListRequest(CourseSortType.RECOMMEND),
                new CourseQueryRepository.CourseLocation(null, null), Map.of(), 10, List.of("바다", "조용"));

        assertThat(rows).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(matching.getId());
    }

    @Test
    void naturalLanguageTermsSearchContentDescriptionAndItsLinkedPlaceOverview() {
        Region region = persistRegion("Busan", "35.1796", "129.0756");
        Course matching = persistCourse(region, "event route", 1);
        Place place = persistPlace(region, "행사장", "바다를 바라보는 공간");
        Content content = entityManager.persist(Content.builder().place(place).source(ContentSource.ADMIN)
                .title("주말 행사").description("조용한 음악 감상").build());
        entityManager.persist(CourseItem.builder().course(matching).content(content)
                .itemType(CourseItemType.CONTENT).orderNo(1).build());
        Course unrelated = persistCourse(region, "unrelated event route", 2);
        persistPlaceItem(unrelated, persistPlace(region, "공연장", "신나는 음악"), 1);
        flushAndClear();

        var rows = courseRepository.findCoursesByCursor(courseListRequest(CourseSortType.RECOMMEND),
                new CourseQueryRepository.CourseLocation(null, null), Map.of(), 10, List.of("바다", "조용"));

        assertThat(rows).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(matching.getId());
    }

    @Test
    void naturalLanguageTermsKeepRegionDurationAndSoftDeleteConditions() {
        Region busan = persistRegion("Busan", "35.1796", "129.0756");
        Region seoul = persistRegion("Seoul", "37.5665", "126.9780");
        Course matching = persistCourse(busan, "eligible route", 1);
        persistPlaceItem(matching, persistPlace(busan, "바다", "산책길"), 1);
        Course wrongRegion = persistCourse(seoul, "other region route", 2);
        persistPlaceItem(wrongRegion, persistPlace(seoul, "바다", "산책길"), 1);
        Course wrongDuration = entityManager.persist(Course.builder().region(busan).title("overnight route")
                .courseType(CourseType.OFFICIAL).durationType(DurationType.ONE_NIGHT)
                .transportType(TransportType.CAR).companionType(CompanionType.FRIEND)
                .thumbnailKey("courses/overnight.jpg").build());
        persistPlaceItem(wrongDuration, persistPlace(busan, "바다", "산책길"), 1);
        Course deleted = persistCourse(busan, "deleted route", 3);
        persistPlaceItem(deleted, persistPlace(busan, "바다", "산책길"), 1);
        deleted.delete();
        flushAndClear();
        var request = new CourseReqDTO.CourseListReq(CourseType.OFFICIAL, null, null, busan.getId(),
                null, DurationType.DAY_TRIP, null, CourseSortType.RECOMMEND, null, null, null, null, 20);

        var rows = courseRepository.findCoursesByCursor(request,
                new CourseQueryRepository.CourseLocation(null, null), Map.of(), 10, List.of("바다"));

        assertThat(rows).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(matching.getId());
    }

    @Test
    void naturalLanguageTermsApplyExactTotalDaysAndTransportWithoutCollapsingThreePlusCourses() {
        Region region = persistRegion("Busan", "35.1796", "129.0756");
        Course fourDayWalk = persistLongCourse(region, "4일 일정", 4, TransportType.WALK, 1);
        Course sixDayWalk = persistLongCourse(region, "6일 일정", 6, TransportType.WALK, 2);
        Course sixDayCar = persistLongCourse(region, "차량 6일 일정", 6, TransportType.CAR, 3);
        Place place = persistPlace(region, "전망대", "바다를 바라보는 산책길");
        for (Course course : List.of(fourDayWalk, sixDayWalk, sixDayCar)) {
            persistPlaceItem(course, place, 1);
        }
        flushAndClear();
        var location = new CourseQueryRepository.CourseLocation(null, null);
        var exactDaysRequest = new CourseReqDTO.CourseListReq(CourseType.OFFICIAL, null, null,
                region.getId(), TransportType.WALK, DurationType.THREE_PLUS, null, CourseSortType.RECOMMEND,
                null, null, null, null, 20);

        var exactDaysRows = courseRepository.findCoursesByCursor(exactDaysRequest,
                location, Map.of(), 10, List.of("바다"), 6);

        assertThat(exactDaysRows).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(sixDayWalk.getId());

        var anyDaysRequest = new CourseReqDTO.CourseListReq(CourseType.OFFICIAL, null, null,
                region.getId(), TransportType.WALK, DurationType.THREE_PLUS, null, CourseSortType.RECOMMEND,
                null, null, null, null, 20);
        var anyDaysRows = courseRepository.findCoursesByCursor(anyDaysRequest,
                location, Map.of(), 10, List.of("바다"));

        assertThat(anyDaysRows).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(fourDayWalk.getId(), sixDayWalk.getId());

        var carRequest = new CourseReqDTO.CourseListReq(CourseType.OFFICIAL, null, null,
                region.getId(), TransportType.CAR, DurationType.THREE_PLUS, null, CourseSortType.RECOMMEND,
                null, null, null, null, 20);
        var carRows = courseRepository.findCoursesByCursor(carRequest,
                location, Map.of(), 10, List.of("바다"), 6);

        assertThat(carRows).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(sixDayCar.getId());

        var literalRequest = new CourseReqDTO.CourseListReq(CourseType.OFFICIAL, "일정", null,
                region.getId(), TransportType.WALK, DurationType.THREE_PLUS, null, CourseSortType.RECOMMEND,
                null, null, null, null, 20);
        var literalRows = courseRepository.findCoursesByCursor(literalRequest, location, Map.of(), 10);

        assertThat(literalRows).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(fourDayWalk.getId(), sixDayWalk.getId());
    }

    @Test
    void naturalLanguageTermsPreserveRecommendCursorPagination() {
        Region region = persistRegion("Busan", "35.1796", "129.0756");
        Course first = persistCourse(region, "first route", 1);
        Course second = persistCourse(region, "second route", 2);
        Course third = persistCourse(region, "third route", 3);
        for (Course course : List.of(first, second, third)) {
            persistPlaceItem(course, persistPlace(region, "바다", "산책길"), 1);
            persistPlaceItem(course, persistPlace(region, "바다 전망대", "파도"), 2);
        }
        flushAndClear();
        var location = new CourseQueryRepository.CourseLocation(null, null);
        var firstPage = courseRepository.findCoursesByCursor(courseListRequest(CourseSortType.RECOMMEND),
                location, Map.of(), 2, List.of("바다"));
        var last = firstPage.get(1);
        var secondPage = courseRepository.findCoursesByCursor(
                courseListRequest(CourseSortType.RECOMMEND, last.recommendOrder().toString(), last.courseId()),
                location, Map.of(), 2, List.of("바다"));

        assertThat(firstPage).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(first.getId(), second.getId());
        assertThat(secondPage).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(third.getId());
    }

    @Test
    void literalSearchStillMatchesWholePhraseInTitleOrRegionOnly() {
        Region region = persistRegion("Busan", "35.1796", "129.0756");
        Course titleMatch = persistCourse(region, "조용한 바다 여행", 1);
        Course overviewOnly = persistCourse(region, "another route", 2);
        persistPlaceItem(overviewOnly, persistPlace(region, "전망대", "조용한 바다 여행"), 1);
        Course separateTerms = persistCourse(region, "바다 조용한", 3);
        flushAndClear();
        var request = new CourseReqDTO.CourseListReq(CourseType.OFFICIAL, "조용한 바다", null, null,
                null, null, null, CourseSortType.RECOMMEND, null, null, null, null, 20);

        var rows = courseRepository.findCoursesByCursor(request,
                new CourseQueryRepository.CourseLocation(null, null), Map.of(), 10);
        assertThat(rows).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(titleMatch.getId());

        var regionRequest = new CourseReqDTO.CourseListReq(CourseType.OFFICIAL, "busan", null, null,
                null, null, null, CourseSortType.RECOMMEND, null, null, null, null, 20);
        var regionRows = courseRepository.findCoursesByCursor(regionRequest,
                new CourseQueryRepository.CourseLocation(null, null), Map.of(), 10);
        assertThat(regionRows).extracting(CourseQueryRepository.CourseListRow::courseId)
                .containsExactly(titleMatch.getId(), overviewOnly.getId(), separateTerms.getId());
    }

    private Place persistPlace(Region region, String name, String overview) {
        return entityManager.persist(Place.builder().region(region).source(PlaceSource.KAKAO)
                .externalPlaceId(java.util.UUID.randomUUID().toString()).name(name).overview(overview)
                .latitude(new BigDecimal("35.1796")).longitude(new BigDecimal("129.0756")).build());
    }

    private void persistPlaceItem(Course course, Place place, int orderNo) {
        entityManager.persist(CourseItem.builder().course(course).place(place)
                .itemType(CourseItemType.PLACE).orderNo(orderNo).build());
    }

    private CourseReqDTO.CourseListReq courseListRequest(CourseSortType sort) {
        return courseListRequest(CourseType.OFFICIAL, sort, null, null);
    }

    private CourseReqDTO.CourseListReq courseListRequest(
            CourseType courseType,
            CourseSortType sort
    ) {
        return courseListRequest(courseType, sort, null, null);
    }

    private CourseReqDTO.CourseListReq courseListRequest(
            CourseSortType sort,
            String cursorValue,
            Long cursorId
    ) {
        return courseListRequest(CourseType.OFFICIAL, sort, cursorValue, cursorId);
    }

    private CourseReqDTO.CourseListReq courseListRequest(
            CourseType courseType,
            CourseSortType sort,
            String cursorValue,
            Long cursorId
    ) {
        return new CourseReqDTO.CourseListReq(
                courseType,
                null,
                null,
                null,
                null,
                null,
                null,
                sort,
                null,
                null,
                cursorValue,
                cursorId,
                20
        );
    }

    private Region persistRegion(String name, String latitude, String longitude) {
        return entityManager.persistFlushFind(Region.builder()
                .name(name)
                .fullName(name)
                .type(RegionType.REGION)
                .latitude(new BigDecimal(latitude))
                .longitude(new BigDecimal(longitude))
                .build());
    }

    private Course persistCourse(Region region, String title) {
        return persistCourse(region, title, null);
    }

    private Course persistCourse(Region region, String title, Integer recommendOrder) {
        return entityManager.persistFlushFind(Course.builder()
                .region(region)
                .title(title)
                .courseType(CourseType.OFFICIAL)
                .recommendOrder(recommendOrder)
                .durationType(DurationType.DAY_TRIP)
                .transportType(TransportType.CAR)
                .companionType(CompanionType.FRIEND)
                .thumbnailKey("courses/" + title + ".jpg")
                .build());
    }

    private Course persistLongCourse(Region region, String title, int totalDays,
            TransportType transportType, int recommendOrder) {
        return entityManager.persist(Course.builder().region(region).title(title)
                .courseType(CourseType.OFFICIAL).recommendOrder(recommendOrder)
                .durationType(DurationType.THREE_PLUS).totalDays(totalDays).transportType(transportType)
                .companionType(CompanionType.FRIEND).thumbnailKey("courses/long.jpg").build());
    }

    private Course persistLocalCourse(Region region, User user, String title) {
        return entityManager.persistFlushFind(Course.builder()
                .region(region)
                .user(user)
                .title(title)
                .courseType(CourseType.LOCAL)
                .durationType(DurationType.DAY_TRIP)
                .transportType(TransportType.CAR)
                .companionType(CompanionType.FRIEND)
                .thumbnailKey("courses/" + title + ".jpg")
                .build());
    }

    private User persistUser(Region region, String email) {
        return entityManager.persistFlushFind(User.builder()
                .nickname(email.substring(0, email.indexOf('@')))
                .email(email)
                .gender(Gender.MALE)
                .birthYear("1990")
                .region(region)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private void persistLike(Course course, User user) {
        entityManager.persist(CourseLike.builder()
                .course(course)
                .user(user)
                .build());
    }

    private void persistReview(Course course, User user) {
        entityManager.persist(CourseReview.builder()
                .course(course)
                .user(user)
                .rating(5)
                .content("good")
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
