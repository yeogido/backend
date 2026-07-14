package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseLike;
import com.yeogido.backend.domain.course.entity.CourseReview;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.domain.course.repository.CourseLikeRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.course.repository.CourseReviewRepository;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    private static final Long MOCK_MEMBER_ID = 1L;
    private static final BigDecimal MIN_RATING = BigDecimal.ZERO;
    private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);

    private final CourseRepository courseRepository;
    private final CourseLikeRepository courseLikeRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final UserRepository userRepository;

    @Override
    public CourseResDTO.CourseCreateRes createCourse(CourseReqDTO.CourseCreateReq request) {
        // TODO: 추천 코스 등록 로직 구현
        return new CourseResDTO.CourseCreateRes(15L);
    }

    @Override
    public CourseResDTO.CourseCreateRes updateCourse(Long courseId, CourseReqDTO.CourseCreateReq request) {
        // TODO: 추천 코스 수정 로직 구현
        return new CourseResDTO.CourseCreateRes(15L);
    }

    @Override
    public CursorResponse<CourseResDTO.CoursePreview> getCourses(CourseReqDTO.CourseListReq request) {
        // TODO: 추천 코스 조회 로직 구현
        return CursorResponse.of(List.of(createFirstMockCourse()), 1L, true);
    }

    @Override
    public List<CourseResDTO.CoursePreview> getPopularCourses(CourseReqDTO.CoursePopularReq request) {
        // TODO: 인기 추천 코스 미리보기 조회 로직 구현
        return List.of(
                createFirstMockCourse(),
                createSecondMockCourse()
        );
    }

    @Override
    public CourseResDTO.CourseDetail getCourse(Long courseId) {
        // TODO: 추천 코스 상세 조회 로직 구현
        CourseResDTO.CourseItem placeItem = new CourseResDTO.CourseItem(
                1,
                CourseItemType.PLACE,
                11L,
                PlaceSource.KAKAO,
                "123456789",
                "주문진 해변",
                "강원특별자치도 강릉시 해안로 1609",
                "강원특별자치도 강릉시 주문진읍 향호리",
                new BigDecimal("37.9111111"),
                new BigDecimal("128.8211111")
        );

        CourseResDTO.CourseItem contentItem = new CourseResDTO.CourseItem(
                2,
                CourseItemType.CONTENT,
                27L,
                PlaceSource.KAKAO,
                "987654321",
                "강릉 커피축제",
                "강원특별자치도 강릉시 난설헌로 131",
                "강원특별자치도 강릉시 초당동",
                new BigDecimal("37.7911111"),
                new BigDecimal("128.9144444")
        );

        CourseResDTO.Author author = new CourseResDTO.Author(null, null);

        return new CourseResDTO.CourseDetail(
                courseId,
                CourseType.OFFICIAL,
                "강릉 혼자 여행 코스",
                "https://example.com/course1.jpg",
                "바다를 따라 걷고, 감성 가득한 카페와 로컬 맛집을 즐기는 강릉 여행 코스입니다.",
                List.of("여름", "자연", "바다", "카페"),
                DurationType.TWO_NIGHT,
                TransportType.WALK,
                4,
                10,
                CompanionType.SOLO,
                true,
                120L,
                1304L,
                List.of(placeItem, contentItem),
                author
        );
    }

    @Override
    @Transactional
    public CourseResDTO.ReviewCreateRes createCourseReview(Long courseId, CourseReqDTO.ReviewCreateReq request) {
        Course course = getActiveCourse(courseId);

        // TODO: Spring Security 적용 후 로그인 사용자 정보로 변경
        User user = userRepository.getReferenceById(MOCK_MEMBER_ID);

        CourseReview review = CourseReview.builder()
                .user(user)
                .course(course)
                .rating(request.rating())
                .content(request.content())
                .build();

        CourseReview savedReview = courseReviewRepository.save(review);

        return new CourseResDTO.ReviewCreateRes(savedReview.getId());
    }

    @Override
    @Transactional
    public CourseResDTO.CourseLikeRes createCourseLike(Long courseId) {
        Course course = getActiveCourse(courseId);

        // TODO: Spring Security 적용 후 로그인 사용자 정보로 변경
        User user = userRepository.getReferenceById(MOCK_MEMBER_ID);

        if (!courseLikeRepository.existsByUserIdAndCourseId(user.getId(), courseId)) {
            CourseLike courseLike = CourseLike.builder()
                    .user(user)
                    .course(course)
                    .build();

            courseLikeRepository.save(courseLike);
        }

        return new CourseResDTO.CourseLikeRes(
                true,
                courseLikeRepository.countByCourseId(courseId)
        );
    }

    @Override
    @Transactional
    public CourseResDTO.CourseLikeRes deleteCourseLike(Long courseId) {
        getActiveCourse(courseId);

        // TODO: Spring Security 적용 후 로그인 사용자 정보로 변경
        User user = userRepository.getReferenceById(MOCK_MEMBER_ID);

        courseLikeRepository.findByUserIdAndCourseId(user.getId(), courseId)
                .ifPresent(courseLikeRepository::delete);

        return new CourseResDTO.CourseLikeRes(
                false,
                courseLikeRepository.countByCourseId(courseId)
        );
    }

    private Course getActiveCourse(Long courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new GeneralException(CourseErrorCode.COURSE_NOT_FOUND));
    }

    private CourseResDTO.CoursePreview createFirstMockCourse() {
        return new CourseResDTO.CoursePreview(
                1L,
                "https://example.com/course1.jpg",
                "강릉 혼자 여행 코스",
                "강릉",
                DurationType.DAY_TRIP,
                TransportType.CAR,
                CompanionType.SOLO,
                List.of("여름", "자연", "바다"),
                true
        );
    }

    private CourseResDTO.CoursePreview createSecondMockCourse() {
        return new CourseResDTO.CoursePreview(
                2L,
                "https://example.com/course2.jpg",
                "강릉 힐링 여행",
                "강릉",
                DurationType.ONE_NIGHT,
                TransportType.CAR,
                CompanionType.FRIEND,
                List.of("힐링", "드라이브"),
                false
        );
    }
}
