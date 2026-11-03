package com.yeogido.backend.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yeogido.backend.domain.business.enums.DayOfWeek;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.course.entity.CourseItemTime;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportMode;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.domain.course.popularity.repository.CoursePopularityRankingRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRedisRepository;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.entity.PlaceOperatingDay;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.service.PlaceService;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.global.config.JpaConfig;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.global.swagger.SwaggerExamples;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
@Import({JpaConfig.class, CourseServiceImpl.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:course-detail-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
class CourseDetailIntegrationTest {

    @Autowired private CourseService courseService;
    @Autowired private TestEntityManager entityManager;

    @MockitoBean private PlaceService placeService;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;
    @MockitoBean private CourseRedisRepository courseRedisRepository;
    @MockitoBean private CoursePopularityRankingRedisRepository rankingRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private Region region;
    private Place place;
    private Content content;

    @BeforeEach
    void setUp() {
        region = entityManager.persist(Region.builder().name("부산").type(RegionType.REGION).build());
        place = entityManager.persist(Place.builder().region(region).name("방문 장소")
                .source(PlaceSource.KAKAO).externalPlaceId("detail-place")
                .latitude(BigDecimal.ZERO).longitude(BigDecimal.ZERO).build());
        content = entityManager.persist(Content.builder().place(place).source(ContentSource.TOUR_API)
                .title("방문 행사").thumbnailImage("contents/event.jpg").build());
        entityManager.persist(PlaceOperatingDay.builder().place(place).dayOfWeek(DayOfWeek.MONDAY)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0)).build());
        when(s3Service.getImageUrl(anyString())).thenAnswer(invocation ->
                "https://example.com/" + invocation.getArgument(0, String.class));
    }

    @ParameterizedTest
    @CsvSource({"DAY_TRIP,1", "ONE_NIGHT,2", "TWO_NIGHT,3", "THREE_PLUS,6"})
    void returnsStoredDaysAndItemsSortedByDayThenOrder(DurationType duration, int days) throws Exception {
        Course course = persistCourse(duration, days);
        // DB 조회 정렬 검증을 위해 일차와 방문 순서 모두 역순으로 저장
        for (int day = days; day >= 1; day--) {
            CourseItem second = persistItem(course, day, 2, CourseItemType.CONTENT);
            CourseItem first = persistItem(course, day, 1, CourseItemType.PLACE);
            persistTime(first, second, TransportMode.CAR, 10);
        }

        CourseResDTO.CourseDetail response = detail(course);

        assertThat(response.durationType()).isEqualTo(duration);
        assertThat(response.totalDays()).isEqualTo(days);
        assertThat(response.courseItems()).hasSize(days * 2);
        JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.path("totalDays").asInt()).isEqualTo(days);
        for (int day = 1; day <= days; day++) {
            CourseResDTO.CourseItem first = response.courseItems().get((day - 1) * 2);
            CourseResDTO.CourseItem second = response.courseItems().get((day - 1) * 2 + 1);
            assertThat(first.day()).isEqualTo(day);
            assertThat(first.order()).isEqualTo(1);
            assertThat(first.timesFromPrevious()).isEmpty();
            assertThat(second.day()).isEqualTo(day);
            assertThat(second.order()).isEqualTo(2);
            assertThat(second.timesFromPrevious()).containsExactly(
                    new CourseResDTO.CourseItemTime(TransportMode.CAR, 10));
            assertThat(json.path("courseItems").get((day - 1) * 2).path("day").asInt()).isEqualTo(day);
            assertThat(json.path("courseItems").get((day - 1) * 2 + 1).path("day").asInt()).isEqualTo(day);
        }
        var placeItem = (CourseResDTO.PlaceCourseItem) response.courseItems().getFirst();
        assertThat(placeItem.imageKey()).isEqualTo("courses/place.jpg");
        assertThat(placeItem.imageUrl()).isEqualTo("https://example.com/courses/place.jpg");
        assertThat(placeItem.operatingDays()).containsExactly(new CourseResDTO.OperatingDay(
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        assertThat(response.courseItems().get(1).imageUrl()).isEqualTo("https://example.com/contents/event.jpg");
        assertThat(response.canManage()).isFalse();
    }

    @Test
    void returnsOnlyTimesFromImmediatelyPreviousItemWithinSameDay() {
        Course course = persistCourse(DurationType.TWO_NIGHT, 3);
        CourseItem day3First = persistItem(course, 3, 1, CourseItemType.PLACE);
        CourseItem day1Third = persistItem(course, 1, 3, CourseItemType.PLACE);
        CourseItem day2Second = persistItem(course, 2, 2, CourseItemType.PLACE);
        CourseItem day1Second = persistItem(course, 1, 2, CourseItemType.CONTENT);
        CourseItem day2First = persistItem(course, 2, 1, CourseItemType.CONTENT);
        CourseItem day1First = persistItem(course, 1, 1, CourseItemType.PLACE);
        persistTime(day1First, day1Second, TransportMode.CAR, 5);
        persistTime(day1First, day1Second, TransportMode.WALK, 12);
        persistTime(day1First, day1Second, TransportMode.PUBLIC, 8);
        persistTime(day1Second, day1Third, TransportMode.CAR, 7);
        persistTime(day2First, day2Second, TransportMode.WALK, 20);
        // 조회 시 제외되어야 하는 일차 간 이동시간과 비인접 항목 간 이동시간 구성
        persistTime(day1Third, day2First, TransportMode.CAR, 99);
        persistTime(day2Second, day3First, TransportMode.CAR, 99);
        persistTime(day1First, day1Third, TransportMode.WALK, 99);
        persistTime(day1Second, day2Second, TransportMode.CAR, 99);

        List<CourseResDTO.CourseItem> items = detail(course).courseItems();

        assertThat(items).extracting(CourseResDTO.CourseItem::courseItemId).containsExactly(
                day1First.getId(), day1Second.getId(), day1Third.getId(),
                day2First.getId(), day2Second.getId(), day3First.getId());
        assertThat(items.get(0).timesFromPrevious()).isEmpty();
        assertThat(items.get(3).timesFromPrevious()).isEmpty();
        assertThat(items.get(5).timesFromPrevious()).isEmpty();
        assertThat(items.get(1).timesFromPrevious()).containsExactly(
                new CourseResDTO.CourseItemTime(TransportMode.WALK, 12),
                new CourseResDTO.CourseItemTime(TransportMode.PUBLIC, 8),
                new CourseResDTO.CourseItemTime(TransportMode.CAR, 5));
        assertThat(items.get(2).timesFromPrevious()).containsExactly(
                new CourseResDTO.CourseItemTime(TransportMode.CAR, 7));
        assertThat(items.get(4).timesFromPrevious()).containsExactly(
                new CourseResDTO.CourseItemTime(TransportMode.WALK, 20));
    }

    @Test
    void preservesStoredTotalDaysForLegacyItemsAssignedOnlyToDayOne() {
        Course course = persistCourse(DurationType.TWO_NIGHT, 3);
        CourseItem second = persistItem(course, 1, 2, CourseItemType.CONTENT);
        CourseItem first = persistItem(course, 1, 1, CourseItemType.PLACE);
        persistTime(first, second, TransportMode.CAR, 10);

        CourseResDTO.CourseDetail response = detail(course);

        assertThat(response.totalDays()).isEqualTo(3);
        assertThat(response.courseItems()).extracting(CourseResDTO.CourseItem::day).containsExactly(1, 1);
        assertThat(response.courseItems().get(1).timesFromPrevious()).containsExactly(
                new CourseResDTO.CourseItemTime(TransportMode.CAR, 10));
    }

    @Test
    void returnsEmptyItemsForCourseWithoutSchedule() {
        CourseResDTO.CourseDetail response = detail(persistCourse(DurationType.ONE_NIGHT, 2));

        assertThat(response.totalDays()).isEqualTo(2);
        assertThat(response.courseItems()).isEmpty();
    }

    @Test
    void rejectsDeletedCourse() {
        Course course = persistCourse(DurationType.DAY_TRIP, 1);
        course.delete();

        assertThatThrownBy(() -> detail(course)).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.COURSE_NOT_FOUND));
    }

    @Test
    void swaggerExampleIncludesValidDailyScheduleAndNoTimesAtDayStart() throws Exception {
        JsonNode example = objectMapper.readTree(SwaggerExamples.COURSE_DETAIL).path("result");

        assertThat(example.path("durationType").asText()).isEqualTo("ONE_NIGHT");
        assertThat(example.path("totalDays").asInt()).isEqualTo(2);
        for (int day = 1; day <= 2; day++) {
            boolean hasPlace = false;
            int expectedOrder = 1;
            for (JsonNode item : example.path("courseItems")) {
                if (item.path("day").asInt() != day) {
                    continue;
                }
                assertThat(item.path("order").asInt()).isEqualTo(expectedOrder++);
                if (item.path("order").asInt() == 1) {
                    assertThat(item.path("timesFromPrevious").isArray()).isTrue();
                    assertThat(item.path("timesFromPrevious").size()).isZero();
                }
                hasPlace |= "PLACE".equals(item.path("type").asText());
            }
            assertThat(hasPlace).isTrue();
        }
    }

    private Course persistCourse(DurationType duration, int days) {
        return entityManager.persist(Course.builder().region(region).title("일차별 상세 조회")
                .courseType(CourseType.OFFICIAL).durationType(duration).totalDays(days)
                .thumbnailKey("courses/thumbnail.jpg").build());
    }

    private CourseItem persistItem(Course course, int day, int order, CourseItemType type) {
        return entityManager.persist(CourseItem.builder().course(course).day(day).orderNo(order).itemType(type)
                .place(type == CourseItemType.PLACE ? place : null)
                .content(type == CourseItemType.CONTENT ? content : null)
                .imageKey(type == CourseItemType.PLACE ? "courses/place.jpg" : null).build());
    }

    private void persistTime(CourseItem from, CourseItem to, TransportMode mode, int minutes) {
        entityManager.persist(CourseItemTime.builder().fromCourseItem(from).toCourseItem(to)
                .transportMode(mode).durationMinutes(minutes).build());
    }

    private CourseResDTO.CourseDetail detail(Course course) {
        // 영속성 컨텍스트의 기존 객체가 아닌 실제 DB 조회 결과 검증
        entityManager.flush();
        entityManager.clear();
        return courseService.getCourseDetail(course.getId(), null);
    }
}
