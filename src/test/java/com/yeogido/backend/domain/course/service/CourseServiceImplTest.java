package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.course.repository.CourseHashtagRepository;
import com.yeogido.backend.domain.course.repository.CourseItemRepository;
import com.yeogido.backend.domain.course.repository.CourseLikeRepository;
import com.yeogido.backend.domain.course.repository.CourseRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.course.repository.CourseReviewRepository;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.hashtag.repository.HashtagRepository;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.service.PlaceService;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseLikeRepository courseLikeRepository;

    @Mock
    private CourseHashtagRepository courseHashtagRepository;

    @Mock
    private CourseItemRepository courseItemRepository;

    @Mock
    private HashtagRepository hashtagRepository;

    @Mock
    private PlaceService placeService;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private CourseReviewRepository courseReviewRepository;

    @Mock
    private CourseRedisRepository courseRedisRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private FileService fileService;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private CourseServiceImpl courseService;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createCourseSavesCreatedEventAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        CourseReqDTO.CourseCreateReq request = createRequest();
        Course course = createCourse(10L);
        Hashtag hashtag = Hashtag.builder()
                .hashtagName("sea")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(createUser()));
        when(regionRepository.findById(1L)).thenReturn(Optional.of(createRegion()));
        when(courseRepository.save(any(Course.class))).thenReturn(course);
        when(hashtagRepository.findAllById(List.of(1L))).thenReturn(List.of(hashtag));
        when(placeService.getPlaceMap(anyList())).thenReturn(Map.of());
        when(placeService.getOrCreatePlace(any(), any())).thenReturn(createPlace());
        when(fileService.moveToDirectory(anyString(), eq(ImageDirectory.COURSE)))
                .thenAnswer(invocation -> "courses/moved/" + invocation.getArgument(0, String.class));

        CourseResDTO.CourseIdRes response = courseService.createCourse(1L, request);

        assertThat(response.courseId()).isEqualTo(10L);
        verify(courseRedisRepository, never()).saveCreatedEvent(anyLong(), any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(courseRedisRepository).saveCreatedEvent(eq(10L), dateCaptor.capture());
        assertThat(dateCaptor.getValue()).isNotNull();
    }

    @Test
    void createCourseStillReturnsWhenCreatedEventSaveFails() {
        CourseReqDTO.CourseCreateReq request = createRequest();
        Course course = createCourse(10L);
        Hashtag hashtag = Hashtag.builder()
                .hashtagName("sea")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(createUser()));
        when(regionRepository.findById(1L)).thenReturn(Optional.of(createRegion()));
        when(courseRepository.save(any(Course.class))).thenReturn(course);
        when(hashtagRepository.findAllById(List.of(1L))).thenReturn(List.of(hashtag));
        when(placeService.getPlaceMap(anyList())).thenReturn(Map.of());
        when(placeService.getOrCreatePlace(any(), any())).thenReturn(createPlace());
        when(fileService.moveToDirectory(anyString(), eq(ImageDirectory.COURSE)))
                .thenAnswer(invocation -> "courses/moved/" + invocation.getArgument(0, String.class));
        doThrow(new RuntimeException("redis unavailable"))
                .when(courseRedisRepository)
                .saveCreatedEvent(anyLong(), any());

        CourseResDTO.CourseIdRes response = courseService.createCourse(1L, request);

        assertThat(response.courseId()).isEqualTo(10L);
        verify(courseRedisRepository).saveCreatedEvent(anyLong(), any());
    }

    @Test
    void createCourse_WhenUserRoleIsUser_CreatesLocalCourse() {
        CourseReqDTO.CourseCreateReq request = createRequest();
        User user = createUser();
        Course course = createCourse(10L);
        Hashtag hashtag = Hashtag.builder()
                .hashtagName("sea")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(regionRepository.findById(1L)).thenReturn(Optional.of(createRegion()));
        when(courseRepository.save(any(Course.class))).thenReturn(course);
        when(hashtagRepository.findAllById(List.of(1L))).thenReturn(List.of(hashtag));
        when(placeService.getPlaceMap(anyList())).thenReturn(Map.of());
        when(placeService.getOrCreatePlace(any(), any())).thenReturn(createPlace());
        when(fileService.moveToDirectory(anyString(), eq(ImageDirectory.COURSE)))
                .thenAnswer(invocation -> "courses/moved/" + invocation.getArgument(0, String.class));

        courseService.createCourse(1L, request);

        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(courseCaptor.capture());
        assertThat(courseCaptor.getValue().getCourseType()).isEqualTo(CourseType.LOCAL);
        assertThat(courseCaptor.getValue().getThumbnailKey())
                .isEqualTo("courses/moved/courses/thumbnail/sample.jpg");
    }

    @Test
    void createCourse_WhenUserRoleIsAdmin_CreatesOfficialCourse() {
        CourseReqDTO.CourseCreateReq request = createRequest();
        User adminUser = createAdmin();
        Course course = createCourse(10L);
        Hashtag hashtag = Hashtag.builder()
                .hashtagName("sea")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(regionRepository.findById(1L)).thenReturn(Optional.of(createRegion()));
        when(courseRepository.save(any(Course.class))).thenReturn(course);
        when(hashtagRepository.findAllById(List.of(1L))).thenReturn(List.of(hashtag));
        when(placeService.getPlaceMap(anyList())).thenReturn(Map.of());
        when(placeService.getOrCreatePlace(any(), any())).thenReturn(createPlace());
        when(fileService.moveToDirectory(anyString(), eq(ImageDirectory.COURSE)))
                .thenAnswer(invocation -> "courses/moved/" + invocation.getArgument(0, String.class));

        courseService.createCourse(1L, request);

        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        verify(courseRepository).save(courseCaptor.capture());
        assertThat(courseCaptor.getValue().getCourseType()).isEqualTo(CourseType.OFFICIAL);
        assertThat(courseCaptor.getValue().getThumbnailKey())
                .isEqualTo("courses/moved/courses/thumbnail/sample.jpg");
    }

    private CourseReqDTO.CourseCreateReq createRequest() {
        return new CourseReqDTO.CourseCreateReq(
                "Busan night course",
                1L,
                "Enjoy Busan night views.",
                DurationType.DAY_TRIP,
                TransportType.CAR,
                CompanionType.FRIEND,
                4,
                10,
                "courses/thumbnail/sample.jpg",
                List.of(1L),
                List.of(new CourseReqDTO.CourseItemCreateReq(
                        1,
                        CourseItemType.PLACE,
                        null,
                        "place-1",
                        "AT4",
                        "Gwangalli",
                        "Busan road",
                        null,
                        BigDecimal.valueOf(35.1531698),
                        BigDecimal.valueOf(129.118666),
                        "courses/place/sample.jpg"
                ))
        );
    }

    private Course createCourse(Long courseId) {
        Course course = Course.builder()
                .user(createUser())
                .region(createRegion())
                .title("Busan night course")
                .description("Enjoy Busan night views.")
                .durationType(DurationType.DAY_TRIP)
                .transportType(TransportType.CAR)
                .companionType(CompanionType.FRIEND)
                .monthStart(4)
                .monthEnd(10)
                .thumbnailKey("courses/thumbnail/sample.jpg")
                .build();
        ReflectionTestUtils.setField(course, "id", courseId);
        return course;
    }

    private User createUser() {
        return User.builder()
                .id(1L)
                .nickname("user")
                .email("user@example.com")
                .gender(Gender.MALE)
                .birthYear("2000")
                .region(createRegion())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private User createAdmin() {
        return User.builder()
                .id(1L)
                .nickname("admin")
                .email("admin@example.com")
                .gender(Gender.MALE)
                .birthYear("2000")
                .region(createRegion())
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Region createRegion() {
        return Region.builder()
                .id(1L)
                .name("Busan")
                .fullName("Busan")
                .type(RegionType.REGION)
                .build();
    }

    private Place createPlace() {
        return Place.builder()
                .externalPlaceId("place-1")
                .categoryGroupCode("AT4")
                .name("Gwangalli")
                .roadAddress("Busan road")
                .latitude(BigDecimal.valueOf(35.1531698))
                .longitude(BigDecimal.valueOf(129.118666))
                .build();
    }
}
