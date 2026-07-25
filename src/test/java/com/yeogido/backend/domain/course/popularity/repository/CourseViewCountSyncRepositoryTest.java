package com.yeogido.backend.domain.course.popularity.repository;

import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.global.config.JpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
@ActiveProfiles("test")
class CourseViewCountSyncRepositoryTest {

    @Autowired
    private CourseViewCountSyncRepository courseViewCountSyncRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void increaseViewCountAddsDeltaToPersistedValue() {
        Region region = entityManager.persistFlushFind(Region.builder()
                .name("Busan")
                .fullName("Busan")
                .type(RegionType.REGION)
                .build());

        Course course = entityManager.persistFlushFind(Course.builder()
                .region(region)
                .title("Busan night course")
                .courseType(CourseType.OFFICIAL)
                .build());

        entityManager.clear();

        int updatedRows = courseViewCountSyncRepository.increaseViewCount(course.getId(), 5L);

        Course updatedCourse = entityManager.find(Course.class, course.getId());

        assertThat(updatedRows).isEqualTo(1);
        assertThat(updatedCourse.getViewCount()).isEqualTo(5L);
    }
}
