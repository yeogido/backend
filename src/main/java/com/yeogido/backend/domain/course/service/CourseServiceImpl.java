package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.domain.content.repository.ContentLikeRepository;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.business.enums.DayOfWeek;
import com.yeogido.backend.domain.course.converter.CourseConverter;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO;
import com.yeogido.backend.domain.course.entity.*;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseSortType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportMode;
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
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.hashtag.exception.HashtagErrorCode;
import com.yeogido.backend.domain.hashtag.repository.HashtagRepository;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.entity.PlaceOperatingDay;
import com.yeogido.backend.domain.place.repository.PlaceLikeRepository;
import com.yeogido.backend.domain.place.repository.PlaceOperatingDayRepository;
import com.yeogido.backend.domain.place.service.PlaceService;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.review.enums.ReviewSortType;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
    private static final int POPULAR_LOCAL_COURSE_SIZE = 4;
    private static final int DEFAULT_COURSE_REVIEW_PAGE_SIZE = 10;
    private static final int RECOMMENDED_COURSE_SIZE = 5;

    private final CourseRepository courseRepository;
    private final CourseLikeRepository courseLikeRepository;
    private final CourseHashtagRepository courseHashtagRepository;
    private final CourseItemRepository courseItemRepository;
    private final CourseItemTimeRepository courseItemTimeRepository;
    private final CourseReviewImageRepository courseReviewImageRepository;
    private final HashtagRepository hashtagRepository;
    private final PlaceService placeService;
    private final ContentRepository contentRepository;
    private final PlaceLikeRepository placeLikeRepository;
    private final PlaceOperatingDayRepository placeOperatingDayRepository;
    private final ContentLikeRepository contentLikeRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final CourseRedisRepository courseRedisRepository;
    private final CoursePopularityRankingRedisRepository coursePopularityRankingRedisRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final FileService fileService;
    private final S3Service s3Service;

    @Override
    @Transactional
    public CourseResDTO.CourseIdRes createCourse(Long userId, CourseReqDTO.CourseCreateReq request) {
        validateCourseCreateRequest(request);
        User user = getCurrentUser(userId);
        CourseType courseType = (user.getRole() == UserRole.ADMIN)
                ? CourseType.OFFICIAL
                : CourseType.LOCAL;
        validateRequiredCourseItemImages(request, courseType);

        CourseReqDTO.CourseCreateReq movedRequest = moveCourseImages(request);
        Region courseRegion = getRegion(movedRequest.regionId());

        Course course = courseRepository.save(
                CourseConverter.toCourse(movedRequest, user, courseRegion, courseType)
        );

        saveCourseHashtags(course, movedRequest.hashtagIds());
        saveCourseItems(course, movedRequest.courseItems(), true);
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
        validateRequiredCourseItemImages(request, course.getCourseType());
        CourseReqDTO.CourseUpdateReq movedRequest = moveCourseImages(request);

        course.update(
                movedRequest.title(),
                movedRequest.description(),
                movedRequest.durationType(),
                movedRequest.transportType(),
                movedRequest.companionType(),
                movedRequest.monthStart(),
                movedRequest.monthEnd(),
                movedRequest.thumbnailKey()
        );

        if (movedRequest.hashtagIds() != null) {
            replaceCourseHashtags(course, movedRequest.hashtagIds());
        }

        if (movedRequest.courseItems() != null) {
            replaceCourseItems(course, movedRequest.courseItems());
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

        if (course.getCourseType() == CourseType.LOCAL) {
            removeLocalRankingAfterCommit(courseId);
        }
    }

    @Override
    public CursorResponse<CourseResDTO.CoursePreview> getCourses(
            CourseReqDTO.CourseListReq request,
            Long userId
    ) {
        validateCourseListRequest(request);

        int size = request.size();
        CourseQueryRepository.CourseLocation location = resolveCourseLocation(request, userId);
        List<CourseQueryRepository.CourseListRow> rows =
                courseRepository.findCoursesByCursor(request, location, size + 1);

        boolean hasNext = rows.size() > size;
        if (hasNext) {
            rows = new ArrayList<>(rows.subList(0, size));
        }

        List<Long> courseIds = rows.stream()
                .map(CourseQueryRepository.CourseListRow::courseId)
                .toList();
        Map<Long, List<String>> tagMap = getPopularCourseTagMap(courseIds);
        Set<Long> likedCourseIds = getLikedCourseIds(userId, courseIds);

        List<CourseResDTO.CoursePreview> items = rows.stream()
                .map(row -> toCoursePreview(
                        row,
                        tagMap.getOrDefault(row.courseId(), List.of()),
                        likedCourseIds.contains(row.courseId())
                ))
                .toList();

        CourseQueryRepository.CourseListRow lastRow = rows.isEmpty()
                ? null
                : rows.get(rows.size() - 1);

        return CursorResponse.of(
                items,
                nextCourseCursorValue(request.sort(), lastRow),
                lastRow == null ? null : lastRow.courseId(),
                hasNext
        );
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
    public List<CourseResDTO.CourseLocalPopularPreview> getPopularLocalCourses(Long userId) {
        List<Long> popularCourseIds =
                coursePopularityRankingRedisRepository.findTopLocalCourseIds(POPULAR_LOCAL_COURSE_SIZE);

        List<Long> courseIds = getActivePopularLocalCourseIds(popularCourseIds);
        courseIds.addAll(getSupplementLatestLocalCourseIds(courseIds));

        if (courseIds.isEmpty()) {
            return List.of();
        }

        Map<Long, CourseRepository.CourseLocalPopularProjection> courseMap =
                courseRepository.findLocalPopularCoursesByCourseIds(courseIds)
                        .stream()
                        .collect(Collectors.toMap(
                                CourseRepository.CourseLocalPopularProjection::getCourseId,
                                Function.identity()
                        ));

        Map<Long, List<String>> tagMap = getPopularCourseTagMap(courseIds);
        Set<Long> likedCourseIds = getLikedCourseIds(userId, courseIds);

        return courseIds.stream()
                .map(courseMap::get)
                .filter(Objects::nonNull)
                .map(course -> CourseConverter.toLocalPopularCoursePreview(
                        course,
                        s3Service.getImageUrl(course.getThumbnailKey()),
                        s3Service.getImageUrl(course.getProfileImageKey()),
                        tagMap.getOrDefault(course.getCourseId(), List.of()),
                        likedCourseIds.contains(course.getCourseId())
                ))
                .toList();
    }

    private List<Long> getActivePopularLocalCourseIds(List<Long> popularCourseIds) {
        if (popularCourseIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, CourseRepository.CourseLocalPopularProjection> courseMap =
                courseRepository.findLocalPopularCoursesByCourseIds(popularCourseIds)
                        .stream()
                        .collect(Collectors.toMap(
                                CourseRepository.CourseLocalPopularProjection::getCourseId,
                                Function.identity()
                        ));

        return popularCourseIds.stream()
                .filter(courseMap::containsKey)
                .distinct()
                .limit(POPULAR_LOCAL_COURSE_SIZE)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Long> getSupplementLatestLocalCourseIds(List<Long> existingCourseIds) {
        int supplementSize = POPULAR_LOCAL_COURSE_SIZE - existingCourseIds.size();

        if (supplementSize <= 0) {
            return List.of();
        }

        Pageable pageable = PageRequest.of(0, supplementSize);

        if (existingCourseIds.isEmpty()) {
            return courseRepository.findLatestLocalCourseIds(pageable);
        }

        return courseRepository.findLatestLocalCourseIdsExcluding(
                existingCourseIds,
                pageable
        );
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

        List<CourseItem> courseItemEntities = courseItemRepository.findByCourseIdOrderByOrderNoAsc(courseId);
        Set<Long> likedPlaceIds = getLikedPlaceIds(userId, courseItemEntities);
        Set<Long> likedContentIds = getLikedContentIds(userId, courseItemEntities);
        Map<Long, List<CourseResDTO.OperatingDay>> operatingDayMap = getOperatingDayMap(courseItemEntities);
        Map<Long, List<CourseResDTO.CourseItemTime>> timesFromPreviousMap =
                getTimesFromPreviousMap(courseId, courseItemEntities);

        List<CourseResDTO.CourseItem> courseItems = courseItemEntities.stream()
                .map(courseItem -> CourseConverter.toCourseItem(
                        courseItem,
                        isCourseItemLiked(courseItem, likedPlaceIds, likedContentIds),
                        s3Service::getImageUrl,
                        getOperatingDays(courseItem, operatingDayMap),
                        timesFromPreviousMap.getOrDefault(courseItem.getId(), List.of())
                ))
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

    private Map<Long, List<CourseResDTO.OperatingDay>> getOperatingDayMap(List<CourseItem> courseItems) {
        Set<Long> placeIds = courseItems.stream()
                .filter(courseItem -> courseItem.getItemType() == CourseItemType.PLACE)
                .map(CourseItem::getPlace)
                .filter(Objects::nonNull)
                .map(Place::getId)
                .collect(Collectors.toSet());

        if (placeIds.isEmpty()) {
            return Map.of();
        }

        return placeOperatingDayRepository.findAllByPlace_IdIn(placeIds).stream()
                .sorted(Comparator.comparing(PlaceOperatingDay::getDayOfWeek))
                .collect(Collectors.groupingBy(
                        operatingDay -> operatingDay.getPlace().getId(),
                        Collectors.mapping(CourseConverter::toOperatingDay, Collectors.toList())
                ));
    }

    private List<CourseResDTO.OperatingDay> getOperatingDays(
            CourseItem courseItem,
            Map<Long, List<CourseResDTO.OperatingDay>> operatingDayMap
    ) {
        if (courseItem.getItemType() != CourseItemType.PLACE || courseItem.getPlace() == null) {
            return List.of();
        }

        return operatingDayMap.getOrDefault(courseItem.getPlace().getId(), List.of());
    }

    private Map<Long, List<CourseResDTO.CourseItemTime>> getTimesFromPreviousMap(
            Long courseId,
            List<CourseItem> courseItems
    ) {
        if (courseItems.size() <= 1) {
            return Map.of();
        }

        Map<Long, Long> previousItemIdByItemId = new HashMap<>();
        for (int index = 1; index < courseItems.size(); index++) {
            previousItemIdByItemId.put(
                    courseItems.get(index).getId(),
                    courseItems.get(index - 1).getId()
            );
        }

        return courseItemTimeRepository.findAllByCourseId(courseId).stream()
                .filter(itemTime -> Objects.equals(
                        previousItemIdByItemId.get(itemTime.getToCourseItem().getId()),
                        itemTime.getFromCourseItem().getId()
                ))
                .sorted(Comparator.comparing(CourseItemTime::getTransportMode))
                .collect(Collectors.groupingBy(
                        itemTime -> itemTime.getToCourseItem().getId(),
                        Collectors.mapping(CourseConverter::toCourseItemTime, Collectors.toList())
                ));
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
    public CursorResponse<CourseResDTO.ReviewPreview> getCourseReviews(
            Long courseId,
            CourseReqDTO.CourseReviewListReq request,
            Long userId
    ) {
        if (!courseRepository.existsByIdAndDeletedAtIsNull(courseId)) {
            throw new GeneralException(CourseErrorCode.COURSE_NOT_FOUND);
        }

        int size = resolveCourseReviewSize(request.size());
        ReviewSortType sort = resolveReviewSort(request.sort());

        List<CourseReview> reviews = findCourseReviews(
                courseId,
                request,
                sort,
                PageRequest.of(0, size + 1)
        );
        boolean hasNext = reviews.size() > size;
        List<CourseReview> content = hasNext
                ? reviews.subList(0, size)
                : reviews;

        Map<Long, List<CourseReviewImage>> imageMap = getCourseReviewImageMap(content);

        List<CourseResDTO.ReviewPreview> items = CourseConverter.toReviewPreviews(
                content,
                imageMap,
                userId,
                s3Service::getImageUrl
        );

        Object cursorValue = getNextCourseReviewCursorValue(content, sort);
        Long cursorId = content.isEmpty()
                ? null
                : content.get(content.size() - 1).getId();

        return CursorResponse.of(items, cursorValue, cursorId, hasNext);
    }

    private List<CourseReview> findCourseReviews(
            Long courseId,
            CourseReqDTO.CourseReviewListReq request,
            ReviewSortType sort,
            Pageable pageable
    ) {
        if (request.cursorValue() == null && request.cursorId() == null) {
            return findFirstCourseReviewPage(courseId, sort, pageable);
        }

        if (request.cursorValue() == null || request.cursorId() == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
        }

        validateCourseReviewCursor(courseId, request.cursorId());

        if (sort == ReviewSortType.RATING) {
            return courseReviewRepository.findReviewsByCourseIdOrderByRatingAfterCursor(
                    courseId,
                    parseReviewRatingCursorValue(request.cursorValue()),
                    request.cursorId(),
                    pageable
            );
        }

        return courseReviewRepository.findReviewsByCourseIdOrderByLatestAfterCursor(
                courseId,
                parseReviewLatestCursorValue(request.cursorValue()),
                request.cursorId(),
                pageable
        );
    }

    private List<CourseReview> findFirstCourseReviewPage(
            Long courseId,
            ReviewSortType sort,
            Pageable pageable
    ) {
        if (sort == ReviewSortType.RATING) {
            return courseReviewRepository.findReviewsByCourseIdOrderByRating(courseId, pageable);
        }

        return courseReviewRepository.findLatestReviewsByCourseId(courseId, pageable);
    }

    private void validateCourseReviewCursor(Long courseId, Long cursorId) {
        CourseReview cursorReview = courseReviewRepository.findById(cursorId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_REQUEST));

        if (!cursorReview.getCourse().getId().equals(courseId)) {
            throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
        }
    }

    private LocalDateTime parseReviewLatestCursorValue(String cursorValue) {
        try {
            return LocalDateTime.parse(cursorValue);
        } catch (DateTimeParseException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
        }
    }

    private Integer parseReviewRatingCursorValue(String cursorValue) {
        try {
            return Integer.valueOf(cursorValue);
        } catch (NumberFormatException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
        }
    }

    private int resolveCourseReviewSize(Integer size) {
        if (size == null) {
            return DEFAULT_COURSE_REVIEW_PAGE_SIZE;
        }

        return size;
    }

    private ReviewSortType resolveReviewSort(ReviewSortType sort) {
        if (sort == null) {
            return ReviewSortType.LATEST;
        }

        return sort;
    }

    private Object getNextCourseReviewCursorValue(
            List<CourseReview> reviews,
            ReviewSortType sort
    ) {
        if (reviews.isEmpty()) {
            return null;
        }

        CourseReview lastReview = reviews.get(reviews.size() - 1);

        if (sort == ReviewSortType.RATING) {
            return lastReview.getRating();
        }

        return lastReview.getCreatedAt();
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
                    .map(this::moveReviewImage)
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

        int insertedRows = courseLikeRepository.insertIgnore(course.getId(), user.getId());
        if (insertedRows > 0) {
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

    private CourseReqDTO.CourseCreateReq moveCourseImages(CourseReqDTO.CourseCreateReq request) {
        return new CourseReqDTO.CourseCreateReq(
                request.title(),
                request.regionId(),
                request.description(),
                request.durationType(),
                request.transportType(),
                request.companionType(),
                request.monthStart(),
                request.monthEnd(),
                moveImage(request.thumbnailKey(), ImageDirectory.COURSE),
                request.hashtagIds(),
                moveCourseItemImages(request.courseItems())
        );
    }

    private CourseReqDTO.CourseUpdateReq moveCourseImages(CourseReqDTO.CourseUpdateReq request) {
        return new CourseReqDTO.CourseUpdateReq(
                request.title(),
                request.description(),
                request.durationType(),
                request.transportType(),
                request.companionType(),
                request.monthStart(),
                request.monthEnd(),
                moveImage(request.thumbnailKey(), ImageDirectory.COURSE),
                request.hashtagIds(),
                request.courseItems() == null ? null : moveCourseItemImages(request.courseItems())
        );
    }

    private List<CourseReqDTO.CourseItemCreateReq> moveCourseItemImages(
            List<CourseReqDTO.CourseItemCreateReq> courseItems
    ) {
        return courseItems.stream()
                .map(this::moveCourseItemImage)
                .toList();
    }

    private CourseReqDTO.CourseItemCreateReq moveCourseItemImage(CourseReqDTO.CourseItemCreateReq item) {
        return new CourseReqDTO.CourseItemCreateReq(
                item.order(),
                item.type(),
                item.contentId(),
                item.externalPlaceId(),
                item.categoryGroupCode(),
                item.name(),
                item.roadAddress(),
                item.lotAddress(),
                item.latitude(),
                item.longitude(),
                moveImage(item.imageKey(), ImageDirectory.COURSE),
                item.operatingDays(),
                item.timesFromPrevious()
        );
    }

    private CourseReqDTO.ReviewImageReq moveReviewImage(CourseReqDTO.ReviewImageReq image) {
        return new CourseReqDTO.ReviewImageReq(
                moveImage(image.imageKey(), ImageDirectory.COURSE),
                image.order()
        );
    }

    private String moveImage(String tempKey, ImageDirectory directory) {
        if (!StringUtils.hasText(tempKey)) {
            return tempKey;
        }

        return fileService.moveToDirectory(tempKey, directory);
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

    private void removeLocalRankingAfterCommit(Long courseId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            removeLocalRanking(courseId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                removeLocalRanking(courseId);
            }
        });
    }

    private void removeLocalRanking(Long courseId) {
        try {
            coursePopularityRankingRedisRepository.removeLocalCourseId(courseId);
        } catch (RuntimeException exception) {
            log.warn("Failed to remove course from local popularity ranking. courseId={}", courseId, exception);
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

    private void validateRequiredCourseItemImages(
            CourseReqDTO.CourseCreateReq request,
            CourseType courseType
    ) {
        validateRequiredCourseItemImages(request.courseItems(), courseType);
    }

    private void validateRequiredCourseItemImages(
            CourseReqDTO.CourseUpdateReq request,
            CourseType courseType
    ) {
        if (request.courseItems() == null) {
            return;
        }

        validateRequiredCourseItemImages(request.courseItems(), courseType);
    }

    private void validateRequiredCourseItemImages(
            List<CourseReqDTO.CourseItemCreateReq> courseItems,
            CourseType courseType
    ) {
        if (courseType != CourseType.LOCAL) {
            return;
        }

        for (CourseReqDTO.CourseItemCreateReq item : courseItems) {
            if (item.type() == CourseItemType.PLACE && !StringUtils.hasText(item.imageKey())) {
                throw new GeneralException(CourseErrorCode.PLACE_ITEM_IMAGE_REQUIRED);
            }
        }
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
                || StringUtils.hasText(item.imageKey())
                || item.operatingDays() != null) {
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

    private void saveCourseItems(
            Course course,
            List<CourseReqDTO.CourseItemCreateReq> courseItems,
            boolean saveItemDetails
    ) {
        Map<String, Place> placeMap = placeService.getPlaceMap(courseItems);
        Map<Long, Content> contentMap = getContentMap(courseItems);
        List<CourseItemWithRequest> items = new ArrayList<>();

        for (CourseReqDTO.CourseItemCreateReq item : courseItems) {
            if (item.type() == CourseItemType.PLACE) {
                Place place = placeService.getOrCreatePlace(item, placeMap);
                items.add(new CourseItemWithRequest(
                        CourseConverter.toPlaceCourseItem(course, place, item),
                        item
                ));
                continue;
            }

            Content content = contentMap.get(item.contentId());
            if (content == null) {
                throw new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND);
            }
            items.add(new CourseItemWithRequest(
                    CourseConverter.toContentCourseItem(course, content, item),
                    item
            ));
        }

        courseItemRepository.saveAll(
                items.stream()
                        .map(CourseItemWithRequest::courseItem)
                        .toList()
        );
        if (saveItemDetails) {
            savePlaceOperatingDays(items);
            saveCourseItemTimes(items);
        }
    }

    private void savePlaceOperatingDays(List<CourseItemWithRequest> items) {
        List<PlaceOperatingDay> operatingDays = new ArrayList<>();
        Map<Place, List<CourseReqDTO.PlaceOperatingDayReq>> operatingDaysByPlace = new LinkedHashMap<>();

        for (CourseItemWithRequest item : items) {
            List<CourseReqDTO.PlaceOperatingDayReq> requests = item.request().operatingDays();
            if (item.courseItem().getItemType() != CourseItemType.PLACE || requests == null) {
                continue;
            }

            validateDuplicatePlaceOperatingDays(requests);
            Place place = item.courseItem().getPlace();
            operatingDaysByPlace.put(place, requests);
        }

        for (Map.Entry<Place, List<CourseReqDTO.PlaceOperatingDayReq>> entry : operatingDaysByPlace.entrySet()) {
            Place place = entry.getKey();
            placeOperatingDayRepository.deleteAllByPlace_Id(place.getId());
            operatingDays.addAll(entry.getValue().stream()
                    .map(request -> CourseConverter.toPlaceOperatingDay(place, request))
                    .toList());
        }

        if (!operatingDays.isEmpty()) {
            placeOperatingDayRepository.saveAll(operatingDays);
        }
    }

    private void saveCourseItemTimes(List<CourseItemWithRequest> items) {
        List<CourseItemWithRequest> orderedItems = items.stream()
                .sorted(Comparator.comparing(item -> item.courseItem().getOrderNo()))
                .toList();
        List<CourseItemTime> itemTimes = new ArrayList<>();

        for (int index = 1; index < orderedItems.size(); index++) {
            CourseItemWithRequest fromItem = orderedItems.get(index - 1);
            CourseItemWithRequest toItem = orderedItems.get(index);
            List<CourseReqDTO.CourseItemTimeReq> requests = toItem.request().timesFromPrevious();

            if (requests == null || requests.isEmpty()) {
                continue;
            }

            validateDuplicateCourseItemTimes(requests);
            itemTimes.addAll(requests.stream()
                    .map(request -> CourseConverter.toCourseItemTime(
                            fromItem.courseItem(),
                            toItem.courseItem(),
                            request
                    ))
                    .toList());
        }

        if (!itemTimes.isEmpty()) {
            courseItemTimeRepository.saveAll(itemTimes);
        }
    }

    private void validateDuplicatePlaceOperatingDays(
            List<CourseReqDTO.PlaceOperatingDayReq> operatingDays
    ) {
        Set<DayOfWeek> days = new HashSet<>();

        for (CourseReqDTO.PlaceOperatingDayReq operatingDay : operatingDays) {
            if (!days.add(operatingDay.dayOfWeek())) {
                throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
            }
        }
    }

    private void validateDuplicateCourseItemTimes(List<CourseReqDTO.CourseItemTimeReq> itemTimes) {
        Set<TransportMode> transportModes = new HashSet<>();

        for (CourseReqDTO.CourseItemTimeReq itemTime : itemTimes) {
            if (!transportModes.add(itemTime.transportMode())) {
                throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
            }
        }
    }

    private record CourseItemWithRequest(
            CourseItem courseItem,
            CourseReqDTO.CourseItemCreateReq request
    ) { }

    private void replaceCourseItems(Course course, List<CourseReqDTO.CourseItemCreateReq> courseItems) {
        courseItemTimeRepository.deleteAllByCourseId(course.getId());
        courseItemRepository.deleteAllByCourseId(course.getId());
        saveCourseItems(course, courseItems, true);
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
            case OFFICIAL -> {
                validatePopularCourseRegionExists(request.regionId());

                yield request.regionId() == null
                        ? coursePopularityRankingRedisRepository.findTopOfficialCourseIds(POPULAR_COURSE_SIZE)
                        : coursePopularityRankingRedisRepository.findTopOfficialRegionCourseIds(
                                request.regionId(),
                                POPULAR_COURSE_SIZE
                        );
            }
            case LOCAL -> {
                validatePopularCourseRegionExists(request.regionId());

                yield request.regionId() == null
                        ? coursePopularityRankingRedisRepository.findTopLocalCourseIds(POPULAR_COURSE_SIZE)
                        : coursePopularityRankingRedisRepository.findTopLocalRegionCourseIds(
                                request.regionId(),
                                POPULAR_COURSE_SIZE
                        );
            }
        };
    }

    private void validatePopularCourseRegionExists(Long regionId) {
        if (regionId != null && !regionRepository.existsById(regionId)) {
            throw new GeneralException(RegionErrorCode.REGION_NOT_FOUND);
        }
    }

    private Map<Long, List<String>> getPopularCourseTagMap(List<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }

        return courseHashtagRepository.findHashtagNamesByCourseIdIn(courseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        CourseHashtagRepository.CourseHashtagNameProjection::getCourseId,
                        Collectors.mapping(
                                CourseHashtagRepository.CourseHashtagNameProjection::getHashtagName,
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

    private Set<Long> getLikedPlaceIds(Long userId, List<CourseItem> courseItems) {
        if (userId == null || courseItems.isEmpty()) {
            return Set.of();
        }

        List<Long> placeIds = courseItems.stream()
                .filter(courseItem -> courseItem.getItemType() == CourseItemType.PLACE)
                .map(CourseItem::getPlace)
                .filter(Objects::nonNull)
                .map(Place::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (placeIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(placeLikeRepository.findLikedPlaceIds(userId, placeIds));
    }

    private Set<Long> getLikedContentIds(Long userId, List<CourseItem> courseItems) {
        if (userId == null || courseItems.isEmpty()) {
            return Set.of();
        }

        List<Long> contentIds = courseItems.stream()
                .filter(courseItem -> courseItem.getItemType() == CourseItemType.CONTENT)
                .map(CourseItem::getContent)
                .filter(Objects::nonNull)
                .map(Content::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (contentIds.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(contentLikeRepository.findLikedContentIds(userId, contentIds));
    }

    private boolean isCourseItemLiked(
            CourseItem courseItem,
            Set<Long> likedPlaceIds,
            Set<Long> likedContentIds
    ) {
        if (courseItem.getItemType() == CourseItemType.PLACE) {
            Place place = courseItem.getPlace();
            return place != null && likedPlaceIds.contains(place.getId());
        }

        Content content = courseItem.getContent();
        return content != null && likedContentIds.contains(content.getId());
    }

    private List<Long> getLatestCourseIds(
            CourseType courseType,
            Long regionId
    ) {
        Pageable pageable = PageRequest.of(0, 2);

        if (courseType == CourseType.LOCAL) {
            return regionId == null
                    ? courseRepository.findLatestLocalCourseIds(pageable)
                    : courseRepository.findLatestLocalRegionCourseIds(regionId, pageable);
        }

        if (regionId == null) {
            return courseRepository.findLatestOfficialCourseIds(pageable);
        }

        return courseRepository.findLatestOfficialRegionCourseIds(regionId, pageable);
    }

    private void validateCourseListRequest(CourseReqDTO.CourseListReq request) {
        if (request.courseType() == CourseType.LOCAL
                && CourseSortType.resolve(request.sort()) == CourseSortType.RECOMMEND) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_LIST_SORT);
        }

        boolean firstPage = request.cursorValue() == null && request.cursorId() == null;
        if (!firstPage && (request.cursorValue() == null || request.cursorId() == null)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }

        if (!firstPage) {
            validateCourseCursorValue(request.sort(), request.cursorValue());
        }

        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }

        if (request.size() <= 0) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
    }

    private void validateCourseCursorValue(
            CourseSortType sort,
            String cursorValue
    ) {
        CourseSortType resolvedSort = CourseSortType.resolve(sort);

        try {
            switch (resolvedSort) {
                case DISTANCE -> Double.valueOf(cursorValue);
                case SAVED, REVIEW -> Long.valueOf(cursorValue);
                case LATEST -> LocalDateTime.parse(cursorValue);
                case RECOMMEND -> Integer.valueOf(cursorValue);
            }
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
    }

    private CourseQueryRepository.CourseLocation resolveCourseLocation(
            CourseReqDTO.CourseListReq request,
            Long userId
    ) {
        if (request.sort() != CourseSortType.DISTANCE) {
            return new CourseQueryRepository.CourseLocation(null, null);
        }

        if (request.latitude() != null && request.longitude() != null) {
            return new CourseQueryRepository.CourseLocation(
                    request.latitude().doubleValue(),
                    request.longitude().doubleValue()
            );
        }

        if (userId == null) {
            throw new GeneralException(CourseErrorCode.LOCATION_REQUIRED_FOR_DISTANCE_SORT);
        }

        User user = getCurrentUser(userId);
        Region region = user.getRegion();
        if (region.getLatitude() == null || region.getLongitude() == null) {
            throw new GeneralException(CourseErrorCode.REGION_COORDINATE_NOT_FOUND);
        }

        return new CourseQueryRepository.CourseLocation(
                region.getLatitude().doubleValue(),
                region.getLongitude().doubleValue()
        );
    }

    private Object nextCourseCursorValue(
            CourseSortType sort,
            CourseQueryRepository.CourseListRow row
    ) {
        if (row == null) {
            return null;
        }

        CourseSortType resolvedSort = CourseSortType.resolve(sort);

        return switch (resolvedSort) {
            case DISTANCE -> row.distance();
            case SAVED -> row.savedCount();
            case REVIEW -> row.reviewCount();
            case LATEST -> row.createdAt();
            case RECOMMEND -> row.recommendOrder();
        };
    }

    private CourseResDTO.CoursePreview toCoursePreview(
            CourseQueryRepository.CourseListRow row,
            List<String> tags,
            boolean isLiked
    ) {
        return new CourseResDTO.CoursePreview(
                row.courseId(),
                s3Service.getImageUrl(row.thumbnailKey()),
                row.title(),
                row.region(),
                row.durationType(),
                row.transportType(),
                row.companionType(),
                tags,
                isLiked
        );
    }

}
