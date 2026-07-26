package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.course.converter.CourseConverter;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.*;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.domain.course.repository.*;
import com.yeogido.backend.domain.course.repository.CourseHashtagRepository;
import com.yeogido.backend.domain.course.repository.CourseItemRepository;
import com.yeogido.backend.domain.course.repository.CourseLikeRepository;
import com.yeogido.backend.domain.course.popularity.repository.CoursePopularityRankingRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseRepository;
import com.yeogido.backend.domain.course.repository.CourseReviewRepository;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.hashtag.exception.HashtagErrorCode;
import com.yeogido.backend.domain.hashtag.repository.HashtagRepository;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.service.PlaceService;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.review.exception.ReviewErrorCode;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    private static final Long MOCK_MEMBER_ID = 1L;
    private static final int POPULAR_COURSE_SIZE = 2;
    private static final int COURSE_REVIEW_PREVIEW_SIZE = 4;
    private static final int RECOMMENDED_COURSE_SIZE = 5;

    private final CourseRepository courseRepository;
    private final CourseLikeRepository courseLikeRepository;
    private final CourseHashtagRepository courseHashtagRepository;
    private final CourseItemRepository courseItemRepository;
    private final CourseReviewImageRepository courseReviewImageRepository;
    private final HashtagRepository hashtagRepository;
    private final PlaceService placeService;
    private final ContentRepository contentRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final CourseRedisRepository courseRedisRepository;
    private final CoursePopularityRankingRedisRepository coursePopularityRankingRedisRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final S3Service s3Service;

    @Override
    @Transactional
    public CourseResDTO.CourseIdRes createCourse(Long userId, CourseReqDTO.CourseCreateReq request) {
        validateCourseCreateRequest(request);

        User user = getCurrentUser(userId);
        Region courseRegion = getRegion(request.regionId());

        CourseType courseType = (user.getRole() == UserRole.ADMIN)
                ? CourseType.OFFICIAL
                : CourseType.LOCAL;

        Course course = courseRepository.save(
                CourseConverter.toCourse(request, user, courseRegion, courseType)
        );

        saveCourseHashtags(course, request.hashtagIds());
        saveCourseItems(course, request.courseItems());
        saveCreatedEventAfterCommit(course.getId());

        return new CourseResDTO.CourseIdRes(course.getId());
    }

    @Override
    @Transactional
    public CourseResDTO.CourseIdRes updateCourse(Long userId, Long courseId, CourseReqDTO.CourseUpdateReq request) {
        Course course = getActiveCourse(courseId);
        User user = getCurrentUser(userId);

        validateCourseAuthority(course, user);
        validateCourseUpdateRequest(request);

        course.update(
                request.title(),
                request.description(),
                request.durationType(),
                request.transportType(),
                request.companionType(),
                request.monthStart(),
                request.monthEnd(),
                request.thumbnailKey()
        );

        if (request.hashtagIds() != null) {
            replaceCourseHashtags(course, request.hashtagIds());
        }

        if (request.courseItems() != null) {
            replaceCourseItems(course, request.courseItems());
        }

        return new CourseResDTO.CourseIdRes(course.getId());
    }

    @Override
    @Transactional
    public void deleteCourse(Long userId, Long courseId) {
        Course course = getActiveCourse(courseId);
        User user = getCurrentUser(userId);

        validateCourseAuthority(course, user);

        course.delete();
    }

    @Override
    public CursorResponse<CourseResDTO.CoursePreview> getCourses(CourseReqDTO.CourseListReq request) {
        // TODO: 추천 코스 조회 로직 구현
        return CursorResponse.of(List.of(createFirstMockCourse()), 1L, null,true);
    }

    @Override
    public List<CourseResDTO.CoursePreview> getPopularCourses(
            CourseReqDTO.CoursePopularReq request,
            Long userId
    ) {
        List<Long> courseIds = getPopularCourseIds(request);

        if (courseIds.isEmpty()) {
            courseIds = getLatestCourseIds(
                    request.courseType(),
                    request.regionId()
            );
        }

        if (courseIds.isEmpty()) {
            return List.of();
        }

        Map<Long, CourseRepository.CoursePopularProjection> courseMap =
                courseRepository.findPopularCoursesByCourseIds(courseIds)
                        .stream()
                        .collect(Collectors.toMap(
                                CourseRepository.CoursePopularProjection::getCourseId,
                                Function.identity()
                        ));

        Map<Long, List<String>> tagMap = getPopularCourseTagMap(courseIds);
        Set<Long> likedCourseIds = getLikedCourseIds(userId, courseIds);

        return courseIds.stream()
                .map(courseMap::get)
                .filter(Objects::nonNull)
                .map(course -> CourseConverter.toPopularCoursePreview(
                        course,
                        s3Service.getImageUrl(course.getThumbnailKey()),
                        tagMap.getOrDefault(course.getCourseId(), List.of()),
                        likedCourseIds.contains(course.getCourseId())
                ))
                .toList();
    }

    @Override
    public List<CourseResDTO.CourseRecommendedPreview> getRecommendedCourses() {
        Pageable pageable = PageRequest.of(0, RECOMMENDED_COURSE_SIZE);

        return courseRepository.findRecommendedCourses(pageable)
                .stream()
                .map(course -> CourseConverter.toRecommendedCoursePreview(
                        course,
                        s3Service.getImageUrl(course.getThumbnailKey())
                ))
                .toList();
    }

    @Override
    public CourseResDTO.CourseDetail getCourseDetail(Long courseId, Long userId) {
        Course course = courseRepository.findCourseDetailByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new GeneralException(CourseErrorCode.COURSE_NOT_FOUND));

        courseRedisRepository.increaseViewCount(courseId, LocalDate.now());

        boolean isLiked = (userId != null) && courseLikeRepository.existsByUserIdAndCourseId(userId, courseId);

        List<String> tags = courseHashtagRepository.findByCourseId(courseId).stream()
                .map(courseHashtag -> courseHashtag.getHashtag().getHashtagName())
                .toList();

        List<CourseResDTO.CourseItem> courseItems = courseItemRepository
                .findByCourseIdOrderByOrderNoAsc(courseId)
                .stream()
                .map(CourseConverter::toCourseItem)
                .toList();

        String profileImageUrl = null;
        if (course.getCourseType() == CourseType.LOCAL && course.getUser() != null) {
            profileImageUrl = s3Service.getImageUrl(course.getUser().getProfileImage());
        }

        return CourseConverter.toCourseDetail(
                course,
                s3Service.getImageUrl(course.getThumbnailKey()),
                tags,
                isLiked,
                courseItems,
                profileImageUrl
        );
    }

    @Override
    public CourseResDTO.CourseSummary getCourseSummary(Long courseId, Long userId) {
        getCurrentUser(userId);

        CourseRepository.CourseSummaryProjection summary = courseRepository.findSummaryByCourseId(courseId)
                .orElseThrow(() -> new GeneralException(CourseErrorCode.COURSE_NOT_FOUND));

        return CourseConverter.toCourseSummary(
                summary,
                s3Service.getImageUrl(summary.getThumbnailKey())
        );
    }

    @Override
    public List<CourseResDTO.ReviewPreview> getCourseReviews(Long courseId) {
        if (!courseRepository.existsByIdAndDeletedAtIsNull(courseId)) {
            throw new GeneralException(CourseErrorCode.COURSE_NOT_FOUND);
        }

        List<CourseReview> reviews = courseReviewRepository.findLatestReviewsByCourseId(
                courseId,
                PageRequest.of(0, COURSE_REVIEW_PREVIEW_SIZE)
        );
        Map<Long, List<CourseReviewImage>> imageMap = getCourseReviewImageMap(reviews);

        return CourseConverter.toReviewPreviews(
                reviews,
                imageMap,
                s3Service::getImageUrl
        );
    }

    @Override
    @Transactional
    public CourseResDTO.ReviewCreateRes createCourseReview(Long userId, Long courseId, CourseReqDTO.ReviewCreateReq request) {
        Course course = getActiveCourse(courseId);
        User user = getCurrentUser(userId);

        validateReviewImages(request.images());

        // 리뷰 엔티티 생성 및 저장
        CourseReview review = CourseConverter.toCourseReview(request, user, course);
        CourseReview savedReview = courseReviewRepository.save(review);

        // 리뷰 이미지 저장 (선택)
        if (request.images() != null && !request.images().isEmpty()) {
            List<CourseReviewImage> reviewImages = request.images().stream()
                    .map(imgReq -> CourseConverter.toCourseReviewImage(savedReview, imgReq))
                    .toList();

            courseReviewImageRepository.saveAll(reviewImages);
        }

        return new CourseResDTO.ReviewCreateRes(savedReview.getId());
    }

    @Override
    @Transactional
    public CourseResDTO.CourseLikeRes createCourseLike(Long userId, Long courseId) {
        Course course = getActiveCourse(courseId);
        User user = getCurrentUser(userId);

        if (!courseLikeRepository.existsByUserIdAndCourseId(user.getId(), courseId)) {
            CourseLike courseLike = CourseConverter.toCourseLike(user, course);

            courseLikeRepository.save(courseLike);
            courseRedisRepository.increaseLikeCount(courseId);
        }

        return new CourseResDTO.CourseLikeRes(
                true,
                courseLikeRepository.countByCourseId(courseId)
        );
    }

    @Override
    @Transactional
    public CourseResDTO.CourseLikeRes deleteCourseLike(Long userId, Long courseId) {
        getActiveCourse(courseId);
        User user = getCurrentUser(userId);

        courseLikeRepository.findByUserIdAndCourseId(user.getId(), courseId)
                .ifPresent(courseLike -> {
                    courseLikeRepository.delete(courseLike);
                    courseRedisRepository.decreaseLikeCount(courseId);
                });

        return new CourseResDTO.CourseLikeRes(
                false,
                courseLikeRepository.countByCourseId(courseId)
        );
    }

    private void validateReviewImages(List<CourseReqDTO.ReviewImageReq> images) {
        if (images == null || images.isEmpty()) {
            return;
        }

        Set<Integer> imageOrders = new HashSet<>();
        for (CourseReqDTO.ReviewImageReq image : images) {
            if (!imageOrders.add(image.order())) {
                throw new GeneralException(ReviewErrorCode.DUPLICATE_IMAGE_ORDER);
            }
        }

        for (int order = 1; order <= images.size(); order++) {
            if (!imageOrders.contains(order)) {
                throw new GeneralException(ReviewErrorCode.INVALID_IMAGE_ORDER);
            }
        }
    }

    private Course getActiveCourse(Long courseId) {
        return courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new GeneralException(CourseErrorCode.COURSE_NOT_FOUND));
    }

    private void saveCreatedEventAfterCommit(Long courseId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            saveCreatedEvent(courseId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                saveCreatedEvent(courseId);
            }
        });
    }

    private void saveCreatedEvent(Long courseId) {
        try {
            courseRedisRepository.saveCreatedEvent(courseId, LocalDate.now());
        } catch (RuntimeException exception) {
            log.warn("Failed to save course created event. courseId={}", courseId, exception);
        }
    }

    private User getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new GeneralException(UserErrorCode.USER_NOT_FOUND);
        }

        return user;
    }

    private Region getRegion(Long regionId) {
        return regionRepository.findById(regionId)
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));
    }

    private void validateCourseAuthority(Course course, User user) {
        switch (course.getCourseType()) {
            case OFFICIAL -> {
                if (user.getRole() != UserRole.ADMIN) {
                    throw new GeneralException(GeneralErrorCode.FORBIDDEN);
                }
            }

            case LOCAL -> {
                if (course.getUser() == null ||
                        !Objects.equals(course.getUser().getId(), user.getId())) {
                    throw new GeneralException(GeneralErrorCode.FORBIDDEN);
                }
            }

            default -> throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    private void validateCourseCreateRequest(CourseReqDTO.CourseCreateReq request) {
        validateHashtags(request.hashtagIds());
        validateCourseItems(request.courseItems());
    }

    private void validateCourseUpdateRequest(CourseReqDTO.CourseUpdateReq request) {
        if (request.hashtagIds() != null) {
            validateHashtags(request.hashtagIds());
        }

        if (request.courseItems() != null) {
            validateCourseItems(request.courseItems());
        }
    }

    private void validateHashtags(List<Long> hashtagIds) {
        if (new HashSet<>(hashtagIds).size() != hashtagIds.size()) {
            throw new GeneralException(CourseErrorCode.DUPLICATE_HASHTAG);
        }
    }

    private void validateCourseItems(List<CourseReqDTO.CourseItemCreateReq> courseItems) {
        Set<Integer> orders = new HashSet<>();
        boolean hasPlace = false;

        for (CourseReqDTO.CourseItemCreateReq item : courseItems) {

            if (!orders.add(item.order())) {
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
        if (item.contentId() == null
                || StringUtils.hasText(item.externalPlaceId())
                || StringUtils.hasText(item.categoryGroupCode())
                || StringUtils.hasText(item.name())
                || StringUtils.hasText(item.roadAddress())
                || StringUtils.hasText(item.lotAddress())
                || item.latitude() != null
                || item.longitude() != null
                || StringUtils.hasText(item.imageKey())) {
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

    private void replaceCourseHashtags(Course course, List<Long> hashtagIds) {
        courseHashtagRepository.deleteAllByCourseId(course.getId());
        saveCourseHashtags(course, hashtagIds);
    }

    private void saveCourseItems(Course course, List<CourseReqDTO.CourseItemCreateReq> courseItems) {
        Map<String, Place> placeMap = placeService.getPlaceMap(courseItems);
        Map<Long, Content> contentMap = getContentMap(courseItems);
        List<CourseItem> items = new ArrayList<>();

        for (CourseReqDTO.CourseItemCreateReq item : courseItems) {
            if (item.type() == CourseItemType.PLACE) {
                Place place = placeService.getOrCreatePlace(item, placeMap);
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

    private void replaceCourseItems(Course course, List<CourseReqDTO.CourseItemCreateReq> courseItems) {
        courseItemRepository.deleteAllByCourseId(course.getId());
        saveCourseItems(course, courseItems);
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

    private List<Long> getPopularCourseIds(CourseReqDTO.CoursePopularReq request) {
        return switch (request.courseType()) {
            case OFFICIAL -> request.regionId() == null
                    ? coursePopularityRankingRedisRepository.findTopOfficialCourseIds(POPULAR_COURSE_SIZE)
                    : coursePopularityRankingRedisRepository.findTopOfficialRegionCourseIds(
                            request.regionId(),
                            POPULAR_COURSE_SIZE
                    );
            case LOCAL -> {
                if (request.regionId() != null) {
                    throw new GeneralException(CourseErrorCode.INVALID_POPULAR_COURSE_REGION);
                }

                yield coursePopularityRankingRedisRepository.findTopLocalCourseIds(POPULAR_COURSE_SIZE);
            }
        };
    }

    private Map<Long, List<String>> getPopularCourseTagMap(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }

        return courseHashtagRepository.findByCourseIdIn(courseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        courseHashtag -> courseHashtag.getCourse().getId(),
                        Collectors.mapping(
                                courseHashtag -> courseHashtag.getHashtag().getHashtagName(),
                                Collectors.toList()
                        )
                ));
    }

    private Map<Long, List<CourseReviewImage>> getCourseReviewImageMap(List<CourseReview> reviews) {
        List<Long> reviewIds = reviews.stream()
                .map(CourseReview::getId)
                .toList();

        if (reviewIds.isEmpty()) {
            return Map.of();
        }

        return courseReviewImageRepository
                .findAllByCourseReview_IdInOrderByCourseReview_IdAscImageOrderAsc(reviewIds)
                .stream()
                .collect(Collectors.groupingBy(image -> image.getCourseReview().getId()));
    }

    private Set<Long> getLikedCourseIds(Long userId, List<Long> courseIds) {
        if (userId == null || courseIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(courseLikeRepository.findLikedCourseIdsByUserIdAndCourseIdIn(
                userId,
                courseIds
        ));
    }

    private List<Long> getLatestCourseIds(
            CourseType courseType,
            Long regionId
    ) {
        Pageable pageable = PageRequest.of(0, 2);

        if (courseType == CourseType.LOCAL) {
            return courseRepository.findLatestLocalCourseIds(pageable);
        }

        if (regionId == null) {
            return courseRepository.findLatestOfficialCourseIds(pageable);
        }

        return courseRepository.findLatestOfficialRegionCourseIds(regionId, pageable);
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
