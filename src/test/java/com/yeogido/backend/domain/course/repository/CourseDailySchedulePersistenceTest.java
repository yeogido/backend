package com.yeogido.backend.domain.course.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.global.config.JpaConfig;
import java.math.BigDecimal;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(JpaConfig.class)
@ActiveProfiles("test")
class CourseDailySchedulePersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @ParameterizedTest
    @CsvSource({"DAY_TRIP,1", "ONE_NIGHT,2", "TWO_NIGHT,3", "THREE_PLUS,4", ",1"})
    void initializesMissingTotalDaysForExistingCreationFlow(DurationType durationType, int expected) {
        Course course = persistCourse(durationType, null);

        assertThat(entityManager.persistFlushFind(course).getTotalDays()).isEqualTo(expected);
    }

    @Test
    void preservesExplicitTotalDays() {
        Course course = persistCourse(DurationType.THREE_PLUS, 6);

        assertThat(entityManager.persistFlushFind(course).getTotalDays()).isEqualTo(6);
    }

    @Test
    void allowsSameOrderOnDifferentDaysAndDefaultsMissingDayToOne() {
        Course course = persistCourse(DurationType.ONE_NIGHT, 2);
        Place place = persistPlace(course.getRegion());
        CourseItem first = CourseItem.builder()
                .course(course).place(place).itemType(CourseItemType.PLACE).orderNo(1).build();
        entityManager.persistAndFlush(first);
        CourseItem second = CourseItem.builder()
                .course(course).place(place).itemType(CourseItemType.PLACE).day(2).orderNo(1).build();
        entityManager.persistAndFlush(second);
        entityManager.clear();

        assertThat(entityManager.find(CourseItem.class, first.getId()).getDay()).isEqualTo(1);
        assertThat(entityManager.find(CourseItem.class, second.getId()).getDay()).isEqualTo(2);
    }

    @Test
    void rejectsDuplicateOrderWithinSameDay() {
        Course course = persistCourse(DurationType.ONE_NIGHT, 2);
        Place place = persistPlace(course.getRegion());
        entityManager.persistAndFlush(CourseItem.builder()
                .course(course).place(place).itemType(CourseItemType.PLACE).day(2).orderNo(1).build());

        assertThatThrownBy(() -> entityManager.persistAndFlush(CourseItem.builder()
                .course(course).place(place).itemType(CourseItemType.PLACE).day(2).orderNo(1).build()))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private Course persistCourse(DurationType durationType, Integer totalDays) {
        Region region = entityManager.persist(Region.builder()
                .name("부산").fullName("부산광역시").type(RegionType.REGION).build());
        return entityManager.persistAndFlush(Course.builder()
                .region(region).title("일차별 코스").courseType(CourseType.OFFICIAL)
                .durationType(durationType).totalDays(totalDays).build());
    }

    private Place persistPlace(Region region) {
        return entityManager.persistAndFlush(Place.builder()
                .region(region).externalPlaceId("schedule-place").source(PlaceSource.KAKAO)
                .name("방문 장소").latitude(BigDecimal.ZERO).longitude(BigDecimal.ZERO).build());
    }
}
