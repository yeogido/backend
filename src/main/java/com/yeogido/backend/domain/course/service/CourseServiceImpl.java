package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.course.converter.CourseConverter;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseHashtag;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.course.entity.CourseLike;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.domain.course.repository.CourseHashtagRepository;
import com.yeogido.backend.domain.course.repository.CourseItemRepository;
import com.yeogido.backend.domain.course.repository.CourseLikeRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.hashtag.exception.HashtagErrorCode;
import com.yeogido.backend.domain.hashtag.repository.HashtagRepository;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    private static final Long MOCK_MEMBER_ID = 1L;

    private final CourseRepository courseRepository;
    private final CourseLikeRepository courseLikeRepository;
    private final CourseHashtagRepository courseHashtagRepository;
    private final CourseItemRepository courseItemRepository;
    private final HashtagRepository hashtagRepository;
    private final PlaceRepository placeRepository;
    private final ContentRepository contentRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CourseResDTO.CourseCreateRes createCourse(CourseReqDTO.CourseCreateReq request) {
        validateCourseCreateRequest(request);

        User user = getCurrentUser();
        Region courseRegion = getRegion(request.regionId());
        Course course = courseRepository.save(CourseConverter.toCourse(request, user, courseRegion));

        saveCourseHashtags(course, request.hashtagIds());
        saveCourseItems(course, request.courseItems());

        return new CourseResDTO.CourseCreateRes(course.getId());
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
    public CourseResDTO.ReviewCreateRes createCourseReview(Long courseId, CourseReqDTO.ReviewCreateReq request) {
        // TODO: 추천 코스 리뷰 작성 로직 구현
        return new CourseResDTO.ReviewCreateRes(1L);
    }

    @Override
    @Transactional
    public CourseResDTO.CourseLikeRes createCourseLike(Long courseId) {
        Course course = getActiveCourse(courseId);

        // TODO: Spring Security 적용 후 로그인 사용자 정보로 변경
        User user = userRepository.getReferenceById(MOCK_MEMBER_ID);

        if (!courseLikeRepository.existsByUserIdAndCourseId(user.getId(), courseId)) {
            CourseLike courseLike = CourseConverter.toCourseLike(user, course);

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

    private User getCurrentUser() {
        return userRepository.findById(MOCK_MEMBER_ID)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
    }

    private Region getRegion(Long regionId) {
        return regionRepository.findById(regionId)
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));
    }

    private void validateCourseCreateRequest(CourseReqDTO.CourseCreateReq request) {
        validateHashtags(request.hashtagIds());
        validateCourseItems(request.courseItems());
    }

    private void validateHashtags(List<Long> hashtagIds) {
        if (hashtagIds == null || hashtagIds.isEmpty()) {
            throw new GeneralException(CourseErrorCode.HASHTAG_REQUIRED);
        }

        if (hashtagIds.size() > 5) {
            throw new GeneralException(CourseErrorCode.TOO_MANY_HASHTAGS);
        }

        if (hashtagIds.stream().anyMatch(Objects::isNull)) {
            throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
        }

        if (new HashSet<>(hashtagIds).size() != hashtagIds.size()) {
            throw new GeneralException(CourseErrorCode.DUPLICATE_HASHTAG);
        }
    }

    private void validateCourseItems(List<CourseReqDTO.CourseItemCreateReq> courseItems) {
        if (courseItems == null || courseItems.isEmpty()) {
            throw new GeneralException(CourseErrorCode.COURSE_ITEM_REQUIRED);
        }

        Set<Integer> orders = new HashSet<>();
        boolean hasPlace = false;

        for (CourseReqDTO.CourseItemCreateReq item : courseItems) {
            if (item.order() == null || !orders.add(item.order())) {
                throw new GeneralException(CourseErrorCode.DUPLICATE_COURSE_ITEM_ORDER);
            }
            if (item.type() == CourseItemType.PLACE) {
                hasPlace = true;
                validatePlaceItem(item);
                continue;
            }
            if (item.type() == CourseItemType.CONTENT) {
                validateContentItem(item);
                continue;
            }
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_ITEM);
        }

        if (!hasPlace) {
            throw new GeneralException(CourseErrorCode.PLACE_ITEM_REQUIRED);
        }
    }

    private void validatePlaceItem(CourseReqDTO.CourseItemCreateReq item) {
        if (!StringUtils.hasText(item.externalPlaceId())
                || !StringUtils.hasText(item.categoryGroupCode())
                || !StringUtils.hasText(item.name())
                || (!StringUtils.hasText(item.roadAddress()) && !StringUtils.hasText(item.lotAddress()))
                || item.latitude() == null
                || item.longitude() == null) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_ITEM);
        }
    }

    private void validateContentItem(CourseReqDTO.CourseItemCreateReq item) {
        if (item.contentId() == null) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_ITEM);
        }
    }

    private void saveCourseHashtags(Course course, List<Long> hashtagIds) {
        Map<Long, Hashtag> hashtagMap = hashtagRepository.findAllById(hashtagIds).stream()
                .collect(Collectors.toMap(Hashtag::getId, Function.identity()));

        if (hashtagMap.size() != hashtagIds.size()) {
            throw new GeneralException(HashtagErrorCode.HASHTAG_NOT_FOUND);
        }

        List<CourseHashtag> courseHashtags = hashtagIds.stream()
                .map(hashtagId -> CourseConverter.toCourseHashtag(course, hashtagMap.get(hashtagId)))
                .toList();

        courseHashtagRepository.saveAll(courseHashtags);
    }

    private void saveCourseItems(Course course, List<CourseReqDTO.CourseItemCreateReq> courseItems) {
        Map<String, Place> placeMap = getPlaceMap(courseItems);
        Map<Long, Content> contentMap = getContentMap(courseItems);
        List<CourseItem> items = new ArrayList<>();

        for (CourseReqDTO.CourseItemCreateReq item : courseItems) {
            if (item.type() == CourseItemType.PLACE) {
                Place place = getOrCreatePlace(item, placeMap);
                items.add(CourseConverter.toPlaceCourseItem(course, place, item));
                continue;
            }

            Content content = contentMap.get(item.contentId());
            if (content == null) {
                throw new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND);
            }
            items.add(CourseConverter.toContentCourseItem(course, content, item));
        }

        courseItemRepository.saveAll(items);
    }

    private Map<String, Place> getPlaceMap(List<CourseReqDTO.CourseItemCreateReq> courseItems) {
        Set<String> externalPlaceIds = courseItems.stream()
                .filter(item -> item.type() == CourseItemType.PLACE)
                .map(CourseReqDTO.CourseItemCreateReq::externalPlaceId)
                .collect(Collectors.toSet());

        if (externalPlaceIds.isEmpty()) {
            return new HashMap<>();
        }

        return placeRepository.findBySourceAndExternalPlaceIdIn(PlaceSource.KAKAO, externalPlaceIds)
                .stream()
                .collect(Collectors.toMap(Place::getExternalPlaceId, Function.identity()));
    }

    private Map<Long, Content> getContentMap(List<CourseReqDTO.CourseItemCreateReq> courseItems) {
        Set<Long> contentIds = courseItems.stream()
                .filter(item -> item.type() == CourseItemType.CONTENT)
                .map(CourseReqDTO.CourseItemCreateReq::contentId)
                .collect(Collectors.toSet());

        if (contentIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, Content> contentMap = contentRepository.findAllById(contentIds).stream()
                .collect(Collectors.toMap(Content::getId, Function.identity()));

        if (contentMap.size() != contentIds.size()) {
            throw new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND);
        }

        return contentMap;
    }

    private Place getOrCreatePlace(CourseReqDTO.CourseItemCreateReq item, Map<String, Place> placeMap) {
        Place place = placeMap.get(item.externalPlaceId());
        if (place != null) {
            return place;
        }

        Region placeRegion = findRegionByAddress(item.roadAddress(), item.lotAddress());
        Place newPlace = placeRepository.save(CourseConverter.toPlace(item, placeRegion));

        placeMap.put(newPlace.getExternalPlaceId(), newPlace);
        return newPlace;
    }

    private Region findRegionByAddress(String roadAddress, String lotAddress) {
        String address = StringUtils.hasText(roadAddress) ? roadAddress : lotAddress;
        if (!StringUtils.hasText(address)) {
            throw new GeneralException(RegionErrorCode.REGION_NOT_FOUND);
        }

        String[] addressParts = address.trim().split("\\s+");
        if (addressParts.length < 2) {
            throw new GeneralException(RegionErrorCode.REGION_NOT_FOUND);
        }

        String sidoFullName = addressParts[0];
        String sigunguName = addressParts[1];

        Region sido = regionRepository.findByFullName(sidoFullName)
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));

        return regionRepository.findByParentAndName(sido, sigunguName)
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));
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
