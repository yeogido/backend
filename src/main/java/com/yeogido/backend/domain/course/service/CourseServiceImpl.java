package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.global.common.response.CursorResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CourseServiceImpl implements CourseService {

    @Override
    public CourseResDTO.CourseCreateRes createCourse(CourseReqDTO.CourseCreateReq request) {
        // TODO: 추천 코스 등록 로직 구현
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
    public CourseResDTO.ReviewCreateRes createCourseReview(Long courseId, CourseReqDTO.ReviewCreateReq request) {
        // TODO: 추천 코스 리뷰 작성 로직 구현
        return new CourseResDTO.ReviewCreateRes(1L);
    }

    @Override
    public CourseResDTO.CourseLikeRes createCourseLike(Long courseId) {
        // TODO: 추천 코스 좋아요 등록 로직 구현
        return new CourseResDTO.CourseLikeRes(true, 121L);
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
