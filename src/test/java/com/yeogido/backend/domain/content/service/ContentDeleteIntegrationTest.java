package com.yeogido.backend.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.repository.CourseItemRepository;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.service.PlaceService;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.user.service.AdminAuthorizationService;
import com.yeogido.backend.global.config.JpaConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@Import({JpaConfig.class, ContentServiceImpl.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:content-delete-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
// 실제 서비스 트랜잭션의 삭제, 재정렬 커밋 및 유일 제약 검증
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentDeleteIntegrationTest {

    @Autowired private ContentService contentService;
    @Autowired private ContentRepository contentRepository;
    @Autowired private CourseItemRepository itemRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private AdminAuthorizationService adminAuthorizationService;
    @MockitoBean private JPAQueryFactory queryFactory;
    @MockitoBean private ContentExternalLinkService contentExternalLinkService;
    @MockitoBean private PlaceService placeService;
    @MockitoBean private FileService fileService;
    @MockitoBean private S3Service s3Service;

    private Region region;
    private Place place;
    private Content content;

    @BeforeEach
    void setUp() {
        inTransaction(() -> {
            region = Region.builder().name("삭제 검증 지역").type(RegionType.REGION).build();
            entityManager.persist(region);
            place = Place.builder().region(region).name("방문 장소").source(PlaceSource.KAKAO)
                    .externalPlaceId(UUID.randomUUID().toString())
                    .latitude(BigDecimal.ZERO).longitude(BigDecimal.ZERO).build();
            entityManager.persist(place);
            content = Content.builder().place(place).source(ContentSource.TOUR_API).title("삭제 행사").build();
            entityManager.persist(content);
        });
    }

    // 첫 번째, 중간, 마지막 행사 삭제 시 동일 일차 재정렬 및 다른 일차 코스 유지 검증
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void reordersOnlyItemsAfterDeletedContentOnSameDay(int deletedOrder) {
        Course[] courses = new Course[2];
        inTransaction(() -> {
            courses[0] = persistCourse(2);
            courses[1] = persistCourse(1);
            for (int order = 1; order <= 3; order++) {
                persistItem(courses[0], 1, order, order == deletedOrder);
                persistItem(courses[0], 2, order, false);
                persistItem(courses[1], 1, order, false);
            }
        });
        List<CourseItem> before = items(courses[0]);
        List<CourseItem> unrelated = items(courses[1]);

        contentService.deleteContent(content.getId(), 1L);

        assertThat(contentRepository.existsById(content.getId())).isFalse();
        assertThat(items(courses[0])).hasSize(5).allSatisfy(item -> {
            CourseItem original = before.stream().filter(i -> i.getId().equals(item.getId())).findFirst().orElseThrow();
            int expected = original.getOrderNo();
            if (original.getDay() == 1 && expected > deletedOrder) {
                expected--;
            }
            assertThat(item.getDay()).isEqualTo(original.getDay());
            assertThat(item.getOrderNo()).isEqualTo(expected);
        });
        assertThat(items(courses[1])).usingRecursiveComparison().isEqualTo(unrelated);
    }

    // 여러 일차 코스에 반복된 행사 전체 삭제 및 일차별 연속 순서 검증
    @Test
    void removesRepeatedContentAcrossDaysAndCourses() {
        Course[] courses = new Course[2];
        inTransaction(() -> {
            courses[0] = persistCourse(2);
            courses[1] = persistCourse(1);
            // 같은 일차의 반복 행사 및 다른 일차·코스의 동일 행사 구성
            for (int order = 1; order <= 5; order++) {
                persistItem(courses[0], 1, order, order == 2 || order == 4);
            }
            for (int order = 1; order <= 3; order++) {
                persistItem(courses[0], 2, order, order == 2);
                persistItem(courses[1], 1, order, order == 2);
            }
        });

        contentService.deleteContent(content.getId(), 1L);

        assertThat(contentRepository.existsById(content.getId())).isFalse();
        assertThat(items(courses[0])).extracting(CourseItem::getOrderNo).containsExactly(1, 2, 3, 1, 2);
        assertThat(items(courses[0])).extracting(CourseItem::getDay).containsExactly(1, 1, 1, 2, 2);
        assertThat(items(courses[1])).extracting(CourseItem::getOrderNo).containsExactly(1, 2);
        assertThat(items(courses[0])).allSatisfy(item -> assertThat(item.getContent()).isNull());
        assertThat(items(courses[1])).allSatisfy(item -> assertThat(item.getContent()).isNull());
    }

    // 코스에 포함되지 않은 행사 삭제 시 기존 일정 유지 검증
    @Test
    void deletesUnreferencedContentWithoutChangingCourseItems() {
        Course[] courses = new Course[1];
        inTransaction(() -> {
            courses[0] = persistCourse(1);
            persistItem(courses[0], 1, 1, false);
            persistItem(courses[0], 1, 2, false);
        });
        List<CourseItem> before = items(courses[0]);

        contentService.deleteContent(content.getId(), 1L);

        assertThat(contentRepository.existsById(content.getId())).isFalse();
        assertThat(items(courses[0])).usingRecursiveComparison().isEqualTo(before);
    }

    private Course persistCourse(int days) {
        Course course = Course.builder().region(region).title("삭제 검증 코스")
                .courseType(CourseType.OFFICIAL).totalDays(days).build();
        entityManager.persist(course);
        return course;
    }

    private void persistItem(Course course, int day, int order, boolean isContent) {
        entityManager.persist(CourseItem.builder().course(course).day(day).orderNo(order)
                .itemType(isContent ? CourseItemType.CONTENT : CourseItemType.PLACE)
                .content(isContent ? content : null).place(isContent ? null : place).build());
    }

    private List<CourseItem> items(Course course) {
        return itemRepository.findByCourseIdOrderByDayAscOrderNoAsc(course.getId());
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }
}
