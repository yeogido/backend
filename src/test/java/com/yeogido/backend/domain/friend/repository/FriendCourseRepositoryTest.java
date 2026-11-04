package com.yeogido.backend.domain.friend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.global.config.JpaConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:friend-course-test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class FriendCourseRepositoryTest {

    @Autowired
    private FriendCourseRepository friendCourseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Region region;
    private User friend;

    @BeforeEach
    void setUp() {
        region = entityManager.persist(Region.builder()
                .name("서울")
                .fullName("서울특별시")
                .type(RegionType.REGION)
                .build());
        friend = persistUser("friend");
    }

    @Test
    void findsOnlyFriendsLiveLocalCoursesLatestFirstWithCursor() {
        Course older = persistCourse(friend, CourseType.LOCAL, null);
        Course newer = persistCourse(friend, CourseType.LOCAL, null);
        persistCourse(friend, CourseType.OFFICIAL, null);
        persistCourse(friend, CourseType.LOCAL, LocalDateTime.now());
        persistCourse(persistUser("stranger"), CourseType.LOCAL, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(friendCourseRepository.findLocalCourses(friend.getId(), null, PageRequest.of(0, 10)))
                .extracting(Course::getId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(friendCourseRepository.findLocalCourses(friend.getId(), newer.getId(), PageRequest.of(0, 10)))
                .extracting(Course::getId)
                .containsExactly(older.getId());
    }

    private Course persistCourse(User author, CourseType courseType, LocalDateTime deletedAt) {
        return entityManager.persist(Course.builder()
                .region(region)
                .user(author)
                .title("코스")
                .courseType(courseType)
                .totalDays(1)
                .viewCount(0L)
                .deletedAt(deletedAt)
                .build());
    }

    private User persistUser(String name) {
        return entityManager.persist(User.builder()
                .nickname(name)
                .email(name + "@example.com")
                .gender(Gender.MALE)
                .birthYear("1990")
                .region(region)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }
}
