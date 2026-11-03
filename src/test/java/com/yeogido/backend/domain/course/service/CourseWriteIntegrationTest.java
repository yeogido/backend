package com.yeogido.backend.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportMode;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.domain.course.popularity.repository.CoursePopularityRankingRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseItemRepository;
import com.yeogido.backend.domain.course.repository.CourseItemTimeRepository;
import com.yeogido.backend.domain.course.repository.CourseRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.service.PlaceService;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.config.JpaConfig;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, CourseServiceImpl.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:course-write-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
// 서비스의 실제 커밋/롤백 검증을 위해 테스트 공통 트랜잭션 비활성화
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CourseWriteIntegrationTest {

    @Autowired private CourseService courseService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseItemRepository itemRepository;
    @Autowired private CourseItemTimeRepository timeRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private PlaceService placeService;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;
    @MockitoBean private CourseRedisRepository courseRedisRepository;
    @MockitoBean private CoursePopularityRankingRedisRepository rankingRepository;

    private User user;
    private Region region;
    private Place place;
    private Content content;
    private Hashtag hashtag;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            region = Region.builder().name("부산").type(RegionType.REGION).build();
            entityManager.persist(region);
            user = User.builder().region(region).nickname("작성자").email(UUID.randomUUID() + "@test.com")
                    .gender(Gender.FEMALE).birthYear("2000").role(UserRole.USER).status(UserStatus.ACTIVE).build();
            entityManager.persist(user);
            place = Place.builder().region(region).name("방문 장소").source(PlaceSource.KAKAO)
                    .externalPlaceId(UUID.randomUUID().toString()).latitude(BigDecimal.ZERO)
                    .longitude(BigDecimal.ZERO).build();
            entityManager.persist(place);
            content = Content.builder().place(place).source(ContentSource.TOUR_API).title("방문 행사").build();
            entityManager.persist(content);
            hashtag = Hashtag.builder().hashtagName("여행").build();
            entityManager.persist(hashtag);
        });
        when(placeService.getPlaceMap(anyList())).thenReturn(Map.of(place.getExternalPlaceId(), place));
        when(placeService.getOrCreatePlace(any(), anyMap())).thenReturn(place);
        when(fileService.moveToDirectory(anyString(), eq(ImageDirectory.COURSE)))
                .thenAnswer(invocation -> "courses/" + invocation.getArgument(0, String.class));
    }

    @ParameterizedTest
    @CsvSource({"DAY_TRIP,1", "ONE_NIGHT,2", "TWO_NIGHT,3", "THREE_PLUS,6"})
    void createsCourseWithDailyItemsAndTimes(DurationType duration, int days) {
        long id = create(duration, days);

        Course saved = courseRepository.findById(id).orElseThrow();
        assertThat(saved.getDurationType()).isEqualTo(duration);
        assertThat(saved.getTotalDays()).isEqualTo(days);
        assertThat(saved.getThumbnailKey()).isEqualTo("courses/thumbnail.jpg");
        assertThat(itemRepository.findByCourseIdOrderByOrderNoAsc(id)).hasSize(days * 2)
                .allSatisfy(item -> {
                    assertThat(item.getDay()).isBetween(1, days);
                    assertThat(item.getOrderNo()).isBetween(1, 2);
                    if (item.getItemType() == CourseItemType.PLACE) {
                        assertThat(item.getImageKey()).isEqualTo("courses/place.jpg");
                    }
                });
        assertTimes(id, days);
    }

    @ParameterizedTest
    @CsvSource({"ONE_NIGHT,2,TWO_NIGHT,3", "TWO_NIGHT,3,DAY_TRIP,1", "THREE_PLUS,4,THREE_PLUS,6"})
    void replacesEntireScheduleWhenDurationOrDaysChange(DurationType oldDuration, int oldDays,
                                                       DurationType duration, int days) {
        long id = create(oldDuration, oldDays);
        List<Long> previousIds = itemIds(id);

        courseService.updateCourse(user.getId(), id, update(duration, days, items(days)));

        Course saved = courseRepository.findById(id).orElseThrow();
        assertThat(saved.getDurationType()).isEqualTo(duration);
        assertThat(saved.getTotalDays()).isEqualTo(days);
        assertThat(itemIds(id)).hasSize(days * 2).doesNotContainAnyElementsOf(previousIds);
        assertTimes(id, days);
    }

    @Test
    void createsMultiDayCourseWithoutOptionalContentOrTravelTimes() {
        var placesOnly = items(2).stream().filter(item -> item.type() == CourseItemType.PLACE).toList();
        long id = courseService.createCourse(user.getId(), new CourseReqDTO.CourseCreateReq("장소만 있는 코스",
                region.getId(), "설명", DurationType.ONE_NIGHT, TransportType.CAR, CompanionType.SOLO,
                1, 12, "thumbnail.jpg", null, List.of(hashtag.getId()), placesOnly, 2)).courseId();

        assertThat(itemRepository.findByCourseIdOrderByOrderNoAsc(id)).hasSize(2)
                .extracting(CourseItem::getItemType).containsOnly(CourseItemType.PLACE);
        assertThat(timeRepository.findAllByCourseId(id)).isEmpty();
    }

    @Test
    void replacesScheduleUsingExistingDurationWhenPeriodFieldsAreOmitted() {
        long id = create(DurationType.ONE_NIGHT, 2);
        List<Long> previousIds = itemIds(id);

        courseService.updateCourse(user.getId(), id, update(null, null, items(2)));

        assertThat(itemIds(id)).hasSize(4).doesNotContainAnyElementsOf(previousIds);
        assertThat(courseRepository.findById(id).orElseThrow().getTotalDays()).isEqualTo(2);
        assertTimes(id, 2);
    }

    @Test
    void preservesLegacyIncompleteScheduleOnMetadataOnlyEdit() {
        long id = create(DurationType.DAY_TRIP, 1);
        // 전체 3일 중 1일차에만 항목이 배치된 기존 데이터 재현
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Course course = courseRepository.findById(id).orElseThrow();
            course.update(null, null, DurationType.TWO_NIGHT, null, null, null, null, null, null);
            course.updateTotalDays(3);
        });
        List<Long> previousIds = itemIds(id);

        courseService.updateCourse(user.getId(), id, update(null, null, null));

        Course saved = courseRepository.findById(id).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("수정 제목");
        assertThat(saved.getTotalDays()).isEqualTo(3);
        assertThat(itemIds(id)).containsExactlyElementsOf(previousIds);
        assertTimes(id, 1);
    }

    @Test
    void rollsBackPeriodItemsAndTimesWhenSavingReplacementFails() {
        long id = create(DurationType.ONE_NIGHT, 2);
        List<Long> previousIds = itemIds(id);
        List<Long> previousTimeIds = timeRepository.findAllByCourseId(id).stream().map(time -> time.getId()).toList();
        var invalidItems = new ArrayList<>(items(3));
        var invalid = invalidItems.getLast();
        // 기존 일정 삭제 및 새 항목 저장 이후 이동수단 중복 오류를 유발하여 전체 롤백 검증
        invalidItems.set(invalidItems.size() - 1, new CourseReqDTO.CourseItemCreateReq(invalid.order(),
                invalid.type(), invalid.contentId(), null, null, null, null, null, null, null, null, null,
                List.of(new CourseReqDTO.CourseItemTimeReq(TransportMode.CAR, 10),
                        new CourseReqDTO.CourseItemTimeReq(TransportMode.CAR, 20)), invalid.day()));

        assertThatThrownBy(() -> courseService.updateCourse(user.getId(), id,
                update(DurationType.TWO_NIGHT, 3, invalidItems)))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(GeneralErrorCode.INVALID_REQUEST));

        Course saved = courseRepository.findById(id).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("등록 제목");
        assertThat(saved.getDurationType()).isEqualTo(DurationType.ONE_NIGHT);
        assertThat(saved.getTotalDays()).isEqualTo(2);
        assertThat(itemIds(id)).containsExactlyElementsOf(previousIds);
        assertThat(timeRepository.findAllByCourseId(id)).extracting(time -> time.getId())
                .containsExactlyInAnyOrderElementsOf(previousTimeIds);
    }

    @Test
    void rejectsEmptyReplacementAndKeepsExistingItems() {
        long id = create(DurationType.ONE_NIGHT, 2);
        var previousIds = itemIds(id);
        assertThatThrownBy(() -> courseService.updateCourse(user.getId(), id, update(null, null, List.of())))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.PLACE_ITEM_REQUIRED));
        assertThat(itemIds(id)).containsExactlyElementsOf(previousIds);
    }

    private long create(DurationType duration, int days) {
        return courseService.createCourse(user.getId(), new CourseReqDTO.CourseCreateReq("등록 제목", region.getId(),
                "설명", duration, TransportType.CAR, CompanionType.SOLO, 1, 12, "thumbnail.jpg", null,
                List.of(hashtag.getId()), items(days), days)).courseId();
    }

    private CourseReqDTO.CourseUpdateReq update(DurationType duration, Integer days,
                                                List<CourseReqDTO.CourseItemCreateReq> items) {
        return new CourseReqDTO.CourseUpdateReq("수정 제목", null, duration, null, null, null, null,
                null, null, null, items, days);
    }

    private List<CourseReqDTO.CourseItemCreateReq> items(int days) {
        var items = new ArrayList<CourseReqDTO.CourseItemCreateReq>();
        // 요청 배열 순서에 의존하지 않는 저장 검증을 위해 일차 역순 구성
        for (int day = days; day >= 1; day--) {
            items.add(new CourseReqDTO.CourseItemCreateReq(1, CourseItemType.PLACE, null, place.getExternalPlaceId(),
                    null, place.getName(), "도로명 주소", null, place.getLatitude(), place.getLongitude(),
                    "place.jpg", null, null, day));
            items.add(new CourseReqDTO.CourseItemCreateReq(2, CourseItemType.CONTENT, content.getId(),
                    null, null, null, null, null, null, null, null, null,
                    List.of(new CourseReqDTO.CourseItemTimeReq(TransportMode.CAR, 10)), day));
        }
        return items;
    }

    private List<Long> itemIds(long courseId) {
        return itemRepository.findByCourseIdOrderByOrderNoAsc(courseId).stream().map(CourseItem::getId).sorted().toList();
    }

    private void assertTimes(long courseId, int days) {
        assertThat(timeRepository.findAllByCourseId(courseId)).hasSize(days).allSatisfy(time -> {
            assertThat(time.getFromCourseItem().getDay()).isEqualTo(time.getToCourseItem().getDay());
            assertThat(time.getFromCourseItem().getOrderNo()).isEqualTo(1);
            assertThat(time.getToCourseItem().getOrderNo()).isEqualTo(2);
        });
    }
}
