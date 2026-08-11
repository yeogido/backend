package com.yeogido.backend.domain.course.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
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

    private CourseReqDTO.CourseListReq courseListRequest(CourseSortType sort) {
        return courseListRequest(sort, null, null);
    }

    private CourseReqDTO.CourseListReq courseListRequest(
            CourseSortType sort,
            String cursorValue,
            Long cursorId
    ) {
        return new CourseReqDTO.CourseListReq(
                CourseType.OFFICIAL,
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
