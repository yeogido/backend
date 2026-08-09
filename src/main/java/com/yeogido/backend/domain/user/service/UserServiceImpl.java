package com.yeogido.backend.domain.user.service;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.auth.service.RefreshTokenService;
import com.yeogido.backend.domain.business.converter.BusinessPromotionConverter;
import com.yeogido.backend.domain.business.dto.response.BusinessPromotionResponse;
import com.yeogido.backend.domain.business.entity.BusinessPromotion;
import com.yeogido.backend.domain.business.entity.BusinessPromotionImage;
import com.yeogido.backend.domain.business.entity.QBusinessPromotion;
import com.yeogido.backend.domain.business.enums.PromotionStatus;
import com.yeogido.backend.domain.business.repository.BusinessPromotionHashtagRepository;
import com.yeogido.backend.domain.business.repository.BusinessPromotionImageRepository;
import com.yeogido.backend.domain.business.repository.BusinessPromotionRepository;
import com.yeogido.backend.domain.content.entity.ContentLike;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.QContent;
import com.yeogido.backend.domain.content.entity.QContentLike;
import com.yeogido.backend.domain.content.repository.ContentHashtagRepository;
import com.yeogido.backend.domain.content.repository.ContentLikeRepository;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.course.converter.CourseConverter;
import com.yeogido.backend.domain.course.entity.*;
import com.yeogido.backend.domain.course.repository.*;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.place.entity.PlaceLike;
import com.yeogido.backend.domain.place.entity.QPlace;
import com.yeogido.backend.domain.place.entity.QPlaceLike;
import com.yeogido.backend.domain.place.enums.PlaceLikeSourceType;
import com.yeogido.backend.domain.place.repository.PlaceLikeRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.travel.repository.StickerRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordPhotoRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordRepository;
import com.yeogido.backend.domain.travel.repository.TravelRecordStickerRepository;
import com.yeogido.backend.domain.user.converter.UserConverter;
import com.yeogido.backend.domain.user.dto.UserReqDTO;
import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.LikeCategory;
import com.yeogido.backend.domain.user.enums.PostCategory;
import com.yeogido.backend.domain.user.enums.SortType;
import com.yeogido.backend.domain.user.enums.UserStatus;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;
    private final CourseLikeRepository courseLikeRepository;
    private final ContentLikeRepository contentLikeRepository;
    private final PlaceLikeRepository placeLikeRepository;
    private final RegionRepository regionRepository;
    private final CourseHashtagRepository courseHashtagRepository;
    private final CourseItemRepository courseItemRepository;
    private final ContentHashtagRepository contentHashtagRepository;
    private final ContentRepository contentRepository;
    private final CourseRepository courseRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final CourseReviewImageRepository courseReviewImageRepository;
    private final TravelRecordRepository travelRecordRepository;
    private final TravelRecordPhotoRepository travelRecordPhotoRepository;
    private final TravelRecordStickerRepository travelRecordStickerRepository;
    private final StickerRepository stickerRepository;
    private final BusinessPromotionRepository businessPromotionRepository;
    private final BusinessPromotionHashtagRepository businessPromotionHashtagRepository;
    private final BusinessPromotionImageRepository businessPromotionImageRepository;
    private final FileService fileService;
    private final S3Service s3Service;
    private final RefreshTokenService refreshTokenService;

    private final JPAQueryFactory queryFactory;

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<UserResDTO.LikedResponse> getLikedList(
            Long userId,
            LikeCategory category,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size,
            Double latitude,
            Double longitude
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));


        // 위치 정보가 없는 경우 온보딩에서 선택한 지역의 중심 좌표를 기준으로 거리 계산
        Double tempLatitude = latitude;
        Double tempLongitude = longitude;

        if (tempLatitude == null || tempLongitude == null) {
            Region region = user.getRegion();

            if (region != null
                    && region.getLatitude() != null
                    && region.getLongitude() != null) {

                tempLatitude = region.getLatitude().doubleValue();
                tempLongitude = region.getLongitude().doubleValue();
            }
        }

        final Double resolvedLatitude = tempLatitude;
        final Double resolvedLongitude = tempLongitude;


        switch (category) {
            case COURSE: {
                // 사용자가 좋아요한 코스 조회
                List<CourseLike> likes =
                        getCourseLikes(user, keyword, sort, cursorCreatedAt, cursorId, size);

                // 코스별 해시태그를 한 번에 조회
                Map<Long, List<String>> hashtagMap =
                        getCourseHashtagMap(likes);

                // size + 1 조회 결과를 이용해 다음 페이지 존재 여부 확인
                boolean hasNext = likes.size() > size;

                if (hasNext) {
                    likes.remove(size.intValue());
                }

                LocalDateTime nextCreatedAt = null;
                Long nextCursorId = null;

                if (!likes.isEmpty()) {
                    CourseLike last = likes.get(likes.size() - 1);
                    nextCreatedAt = last.getCreatedAt();
                    nextCursorId = last.getId();
                }

                List<UserResDTO.LikedResponse> result =
                        likes.stream()
                                .map(like -> UserConverter.toLikedResponse(
                                        like,
                                        hashtagMap.getOrDefault(
                                                like.getCourse().getId(),
                                                List.of()
                                        ),
                                        s3Service.getImageUrl(like.getCourse().getThumbnailKey())
                                ))
                                .toList();

                return CursorResponse.of(
                        result,
                        nextCreatedAt,
                        nextCursorId,
                        hasNext
                );
            }
            case CONTENT: {
                // 사용자가 좋아요한 콘텐츠 조회
                List<ContentLike> likes =
                        getContentLikes(user, keyword, sort, cursorCreatedAt, cursorId, size);

                // 콘텐츠별 해시태그를 한 번에 조회
                Map<Long, List<String>> hashtagMap =
                        getContentHashtagMap(likes);


                boolean hasNext = likes.size() > size;

                if (hasNext) {
                    likes.remove(size.intValue());
                }

                LocalDateTime nextCreatedAt = null;
                Long nextCursorId = null;

                if (!likes.isEmpty()) {
                    ContentLike last = likes.get(likes.size() - 1);
                    nextCreatedAt = last.getCreatedAt();
                    nextCursorId = last.getId();
                }

                List<UserResDTO.LikedResponse> result =
                        likes.stream()
                                .map(like -> UserConverter.toLikedResponse(
                                        like,
                                        hashtagMap.getOrDefault(
                                                like.getContent().getId(),
                                                List.of()
                                        ),
                                        s3Service.getImageUrl(like.getContent().getThumbnailImage())
                                ))
                                .toList();

                return CursorResponse.of(
                        result,
                        nextCreatedAt,
                        nextCursorId,
                        hasNext
                );
            }
            case PLACE: {
                // 사용자가 좋아요한 장소 조회
                List<PlaceLike> likes =
                        getPlaceLikes(user, keyword, sort, cursorCreatedAt, cursorId, size);

                boolean hasNext = likes.size() > size;

                if (hasNext) {
                    likes.remove(size.intValue());
                }

                LocalDateTime nextCreatedAt = null;
                Long nextCursorId = null;

                if (!likes.isEmpty()) {
                    PlaceLike last = likes.get(likes.size() - 1);
                    nextCreatedAt = last.getCreatedAt();
                    nextCursorId = last.getId();
                }

                PlaceLikeImageSources imageSources = getPlaceLikeImageSources(likes);

                List<UserResDTO.LikedResponse> result =
                        likes.stream()
                                .map(like -> UserConverter.toLikedResponse(
                                        like,
                                        resolvedLatitude,
                                        resolvedLongitude,
                                        imageSources.thumbnailUrl(like)
                                ))
                                .toList();

                return CursorResponse.of(
                        result,
                        nextCreatedAt,
                        nextCursorId,
                        hasNext
                );
            }
            case ALL:
                return getAllLikes(user, keyword, sort, cursorCreatedAt, cursorId, size, resolvedLatitude, resolvedLongitude);
        }
        throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }


    private List<CourseLike> getCourseLikes(
            User user,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        QCourseLike courseLike = QCourseLike.courseLike;

        OrderSpecifier<?> createdAtOrder =
                sort == SortType.LATEST
                        ? courseLike.createdAt.desc()
                        : courseLike.createdAt.asc();

        OrderSpecifier<?> idOrder =
                sort == SortType.LATEST
                        ? courseLike.id.desc()
                        : courseLike.id.asc();

        return queryFactory
                .selectFrom(courseLike)
                .join(courseLike.course).fetchJoin()
                .leftJoin(courseLike.course.region).fetchJoin()
                .where(
                        courseLike.user.eq(user),
                        courseTitleContains(keyword),
                        cursorCondition(courseLike.createdAt,
                                courseLike.id,
                                cursorCreatedAt,
                                cursorId,
                                sort)
                )
                .orderBy(createdAtOrder, idOrder)
                .limit(size + 1)
                .fetch();

    }


    private List<ContentLike> getContentLikes(
            User user,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        QContentLike contentLike = QContentLike.contentLike;

        OrderSpecifier<?> createdAtOrder =
                sort == SortType.LATEST
                        ? contentLike.createdAt.desc()
                        : contentLike.createdAt.asc();

        OrderSpecifier<?> idOrder =
                sort == SortType.LATEST
                        ? contentLike.id.desc()
                        : contentLike.id.asc();

        return queryFactory
                .selectFrom(contentLike)
                .join(contentLike.content).fetchJoin()
                .leftJoin(contentLike.content.place).fetchJoin()
                .where(
                        contentLike.user.eq(user),
                        contentTitleContains(keyword),
                        cursorCondition(contentLike.createdAt,
                                contentLike.id,
                                cursorCreatedAt,
                                cursorId,
                                sort)
                )
                .orderBy(createdAtOrder, idOrder)
                .limit(size + 1)
                .fetch();

    }


    private List<PlaceLike> getPlaceLikes(
            User user,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        QPlaceLike placeLike = QPlaceLike.placeLike;

        OrderSpecifier<?> createdAtOrder =
                sort == SortType.LATEST
                        ? placeLike.createdAt.desc()
                        : placeLike.createdAt.asc();

        OrderSpecifier<?> idOrder =
                sort == SortType.LATEST
                        ? placeLike.id.desc()
                        : placeLike.id.asc();


        return queryFactory
                .selectFrom(placeLike)
                .join(placeLike.place).fetchJoin()
                .leftJoin(placeLike.place.region).fetchJoin()
                .where(
                        placeLike.user.eq(user),
                        placeNameContains(keyword),
                        cursorCondition(placeLike.createdAt,
                               placeLike.id,
                                cursorCreatedAt,
                                cursorId,
                                sort)
                )
                .orderBy(createdAtOrder, idOrder)
                .limit(size + 1)
                .fetch();

    }

    private CursorResponse<UserResDTO.LikedResponse> getAllLikes(
            User user,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size,
            Double latitude,
            Double longitude
    ) {
        // 카테고리별 좋아요 목록 조회
        List<CourseLike> courseLikes =
                getCourseLikes(user, keyword,sort, cursorCreatedAt, cursorId, size);

        List<ContentLike> contentLikes =
                getContentLikes(user, keyword, sort, cursorCreatedAt, cursorId, size);

        List<PlaceLike> placeLikes =
                getPlaceLikes(user, keyword, sort, cursorCreatedAt, cursorId, size);

        // 해시태그 조회
        Map<Long, List<String>> courseHashtagMap =
                getCourseHashtagMap(courseLikes);

        Map<Long, List<String>> contentHashtagMap =
                getContentHashtagMap(contentLikes);

        PlaceLikeImageSources placeLikeImageSources =
                getPlaceLikeImageSources(placeLikes);


        // 하나의 리스트로 병합
        List<LikeItem> items = new ArrayList<>();

        courseLikes.forEach(like ->
                items.add(toCourseLikeItem(
                        like,
                        courseHashtagMap.getOrDefault(
                                like.getCourse().getId(),
                                List.of()
                        )
                ))
        );

        contentLikes.forEach(like ->
                items.add(toContentLikeItem(
                        like,
                        contentHashtagMap.getOrDefault(
                                like.getContent().getId(),
                                List.of()
                        )
                ))
        );

        placeLikes.forEach(like ->
                items.add(toPlaceLikeItem(
                        like,
                        latitude,
                        longitude,
                        placeLikeImageSources
                ))
        );

        Comparator<LikeItem> comparator =
                Comparator.comparing(LikeItem::createdAt)
                        .thenComparing(LikeItem::likeId);

        if (sort == SortType.LATEST) {
            comparator = comparator.reversed();
        }

        // 최신순/오래된순 정렬
        items.sort(comparator);

        boolean hasNext = items.size() > size;

        List<LikeItem> resultItems = hasNext
                ? new ArrayList<>(items.subList(0, size))
                : items;

        LocalDateTime nextCreatedAt = null;
        Long nextCursorId = null;

        if (!resultItems.isEmpty()) {
            LikeItem last = resultItems.get(resultItems.size() - 1);
            nextCreatedAt = last.createdAt();
            nextCursorId = last.likeId();
        }

        return CursorResponse.of(
                resultItems.stream()
                        .map(LikeItem::response)
                        .toList(),
                nextCreatedAt,
                nextCursorId,
                hasNext
        );
    }

    private record LikeItem(
            LocalDateTime createdAt,
            Long likeId,
            UserResDTO.LikedResponse response
    ) {}

    // CourseLike를 통합 정렬을 위한 LikeItem으로 변환
    private LikeItem toCourseLikeItem(CourseLike like, List<String> hashtags) {
        return new LikeItem(
                like.getCreatedAt(),
                like.getId(),
                UserConverter.toLikedResponse(
                        like,
                        hashtags,
                        s3Service.getImageUrl(like.getCourse().getThumbnailKey())
                )
        );
    }

    // ContentLike를 통합 정렬을 위한 LikeItem으로 변환
    private LikeItem toContentLikeItem(ContentLike like, List<String> hashtags) {
        return new LikeItem(
                like.getCreatedAt(),
                like.getId(),
                UserConverter.toLikedResponse(
                        like,
                        hashtags,
                        s3Service.getImageUrl(like.getContent().getThumbnailImage())
                )
        );
    }

    // PlaceLike를 통합 정렬을 위한 LikeItem으로 변환
    private LikeItem toPlaceLikeItem(
            PlaceLike like,
            Double latitude,
            Double longitude,
            PlaceLikeImageSources imageSources
    ) {
        return new LikeItem(
                like.getCreatedAt(),
                like.getId(),
                UserConverter.toLikedResponse(
                        like,
                        latitude,
                        longitude,
                        imageSources.thumbnailUrl(like)
                )
        );
    }

    private PlaceLikeImageSources getPlaceLikeImageSources(List<PlaceLike> placeLikes) {
        List<Long> courseItemIds = placeLikes.stream()
                .filter(like -> like.getSourceType() == PlaceLikeSourceType.COURSE_ITEM)
                .map(PlaceLike::getSourceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> courseItemImageUrls = courseItemIds.isEmpty()
                ? Map.of()
                : courseItemRepository.findAllById(courseItemIds).stream()
                        .filter(item -> StringUtils.hasText(item.getImageKey()))
                        .collect(Collectors.toMap(
                                CourseItem::getId,
                                item -> s3Service.getImageUrl(item.getImageKey())
                        ));

        List<Long> contentIds = placeLikes.stream()
                .filter(like -> like.getSourceType() == PlaceLikeSourceType.CONTENT)
                .map(PlaceLike::getSourceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> contentImageUrls = contentIds.isEmpty()
                ? Map.of()
                : contentRepository.findAllById(contentIds).stream()
                        .filter(content -> StringUtils.hasText(content.getThumbnailImage()))
                        .collect(Collectors.toMap(
                                Content::getId,
                                content -> s3Service.getImageUrl(content.getThumbnailImage())
                        ));

        List<Long> promotionIds = placeLikes.stream()
                .filter(like -> like.getSourceType() == PlaceLikeSourceType.PROMOTION)
                .map(PlaceLike::getSourceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> promotionImageUrls = promotionIds.isEmpty()
                ? Map.of()
                : businessPromotionImageRepository
                        .findAllByPromotion_IdInAndSortOrder(promotionIds, 1)
                        .stream()
                        .collect(Collectors.toMap(
                                image -> image.getPromotion().getId(),
                                image -> s3Service.getImageUrl(image.getImageKey())
                        ));

        return new PlaceLikeImageSources(
                courseItemImageUrls,
                contentImageUrls,
                promotionImageUrls
        );
    }

    private record PlaceLikeImageSources(
            Map<Long, String> courseItemImageUrls,
            Map<Long, String> contentImageUrls,
            Map<Long, String> promotionImageUrls
    ) {
        private String thumbnailUrl(PlaceLike like) {
            if (like.getSourceType() == null || like.getSourceId() == null) {
                return null;
            }

            return switch (like.getSourceType()) {
                case COURSE_ITEM -> courseItemImageUrls.get(like.getSourceId());
                case CONTENT -> contentImageUrls.get(like.getSourceId());
                case PROMOTION -> promotionImageUrls.get(like.getSourceId());
            };
        }
    }



    private Map<Long, List<String>> getCourseHashtagMap(
            List<CourseLike> courseLikes
    ) {
        List<Long> courseIds = courseLikes.stream()
                .map(like -> like.getCourse().getId())
                .distinct()
                .toList();

        if (courseIds.isEmpty()) {
            return Map.of();
        }

        return courseHashtagRepository.findByCourseIdIn(courseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        courseHashtag -> courseHashtag.getCourse().getId(),
                        Collectors.mapping(
                                courseHashtag ->
                                        courseHashtag.getHashtag().getHashtagName(),
                                Collectors.toList()
                        )
                ));
    }

    private Map<Long, List<String>> getContentHashtagMap(
            List<ContentLike> contentLikes
    ) {
        List<Long> contentIds = contentLikes.stream()
                .map(like -> like.getContent().getId())
                .distinct()
                .toList();

        if (contentIds.isEmpty()) {
            return Map.of();
        }

        return contentHashtagRepository.findByContentIdIn(contentIds)
                .stream()
                .collect(Collectors.groupingBy(
                        ch -> ch.getContent().getId(),
                        Collectors.mapping(
                                c -> c.getHashtag().getHashtagName(),
                                Collectors.toList()
                        )
                ));
    }

    private BooleanExpression courseTitleContains(String keyword) {
        return StringUtils.hasText(keyword)
                ? QCourse.course.title.containsIgnoreCase(keyword)
                : null;
    }

    private BooleanExpression contentTitleContains(String keyword) {
        return StringUtils.hasText(keyword)
                ? QContent.content.title.containsIgnoreCase(keyword)
                : null;
    }

    private BooleanExpression placeNameContains(String keyword) {
        return StringUtils.hasText(keyword)
                ? QPlace.place.name.containsIgnoreCase(keyword)
                : null;
    }

    private BooleanExpression cursorCondition(
            DateTimePath<LocalDateTime> createdAt,
            NumberPath<Long> id,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            SortType sort
    ) {

        if (cursorCreatedAt == null || cursorId == null) {
            return null;
        }

        if (sort == SortType.LATEST) {
            return createdAt.lt(cursorCreatedAt)
                    .or(
                            createdAt.eq(cursorCreatedAt)
                                    .and(id.lt(cursorId))
                    );
        }

        return createdAt.gt(cursorCreatedAt)
                .or(
                        createdAt.eq(cursorCreatedAt)
                                .and(id.gt(cursorId))
                );
    }

    private BooleanExpression keywordCondition(StringPath title, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return title.containsIgnoreCase(keyword);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResDTO.Profile getMyPage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        return UserConverter.toProfile(user, s3Service.getImageUrl(user.getProfileImage()));
    }

    @Override
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .filter(foundUser -> foundUser.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        cleanupUserOwnedData(userId);
        user.withdraw();
        refreshTokenService.delete(userId);
    }

    private void cleanupUserOwnedData(Long userId) {
        LocalDateTime deletedAt = LocalDateTime.now();

        courseLikeRepository.deleteAllByUserId(userId);
        contentLikeRepository.deleteAllByUserId(userId);
        placeLikeRepository.deleteAllByUserId(userId);

        courseReviewImageRepository.deleteAllByCourseReviewUserId(userId);
        courseReviewRepository.deleteAllByUserId(userId);

        travelRecordStickerRepository.deleteAllByTravelRecordUserId(userId);
        travelRecordPhotoRepository.deleteAllByTravelRecordUserId(userId);
        travelRecordRepository.deleteAllByUserId(userId);

        stickerRepository.softDeleteAllByUserId(userId, deletedAt);
        courseRepository.softDeleteAllByUserId(userId, deletedAt);
        businessPromotionRepository.softDeleteAllActiveByUserId(userId);
    }


    @Override
    public CursorResponse<UserResDTO.MyPostResponse> getMyPosts(
            Long userId,
            PostCategory category,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        switch (category) {

            case COURSE:{
                CursorResponse<UserResDTO.MyCourseResponse> response =
                        getMyCourses(user, keyword, sort, cursorCreatedAt, cursorId, size);

                List<UserResDTO.MyPostResponse> items = response.getItems().stream()
                        .map(course -> CourseConverter.toMyPostResponse(course, null,null))
                        .toList();

                return CursorResponse.of(
                        items,
                        response.getCursorValue(),
                        response.getCursorId(),
                        response.isHasNext()
                );
            }

            case REVIEW: {
                CursorResponse<UserResDTO.MyPostResponse> response =
                        getMyReviews(user, keyword, sort, cursorCreatedAt, cursorId, size);

                return CursorResponse.of(
                        response.getItems(),
                        response.getCursorValue(),
                        response.getCursorId(),
                        response.isHasNext()
                );
            }

            case PROMOTION: {
                CursorResponse<BusinessPromotionResponse.MySummary> response =
                        getMyPromotions(
                                user,
                                keyword,
                                sort,
                                cursorCreatedAt,
                                cursorId,
                                size
                        );

                List<UserResDTO.MyPostResponse> items =
                        response.getItems().stream()
                                .map(promotion ->
                                        CourseConverter.toMyPostResponse(
                                                null,
                                                null,
                                                promotion
                                        )
                                )
                                .toList();

                return CursorResponse.of(
                        items,
                        response.getCursorValue(),
                        response.getCursorId(),
                        response.isHasNext()
                );
            }

            case ALL:
                return getAllMyPosts(
                        user,
                        keyword,
                        sort,
                        cursorCreatedAt,
                        cursorId,
                        size
                );
            default:
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
    }

    private CursorResponse<UserResDTO.MyCourseResponse> getMyCourses(
            User user,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size
    ) {

        QCourse course = QCourse.course;

        OrderSpecifier<?> createdAtOrder =
                sort == SortType.LATEST ? course.createdAt.desc() : course.createdAt.asc();

        OrderSpecifier<?> idOrder =
                sort == SortType.LATEST ? course.id.desc() : course.id.asc();


        List<Course> courses = queryFactory
                .selectFrom(course)
                .where(
                        course.user.eq(user),
                        course.deletedAt.isNull(),
                        keywordCondition(course.title, keyword),
                        cursorCondition(
                                course.createdAt,
                                course.id,
                                cursorCreatedAt,
                                cursorId,
                                sort
                        )
                )
                .orderBy(createdAtOrder, idOrder)
                .limit(size + 1)
                .fetch();


        boolean hasNext = courses.size() > size;

        if (hasNext) {
            courses.remove(size.intValue());
        }

        Map<Long, List<String>> hashtagMap = getCourseHashtagMapFromCourses(courses);

        List<UserResDTO.MyCourseResponse> items = courses.stream()
                .map(c -> {
                    List<String> hashtags =
                            hashtagMap.getOrDefault(c.getId(), List.of());

                    String thumbnailUrl = s3Service.getImageUrl(c.getThumbnailKey());

                    return CourseConverter.toMyCourseResponse(
                            c,
                            thumbnailUrl,
                            hashtags
                    );
                })
                .toList();

        Object nextCursorValue = null;
        Long nextCursorId = null;

        if (!items.isEmpty()) {
            UserResDTO.MyCourseResponse last = items.get(items.size() - 1);
            nextCursorValue = last.createdAt();
            nextCursorId = last.id();
        }

        return CursorResponse.of(
                items,
                nextCursorValue,
                nextCursorId,
                hasNext
        );
    }

    private Map<Long, List<String>> getCourseHashtagMapFromCourses(List<Course> courses) {

        List<Long> courseIds = courses.stream()
                .map(Course::getId)
                .distinct()
                .toList();

        if (courseIds.isEmpty()) {
            return Map.of();
        }

        return courseHashtagRepository.findByCourseIdIn(courseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        ch -> ch.getCourse().getId(),
                        Collectors.mapping(
                                ch -> ch.getHashtag().getHashtagName(),
                                Collectors.toList()
                        )
                ));
    }



    private CursorResponse<UserResDTO.MyPostResponse> getMyReviews(
            User user,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size
    ) {

        QCourseReview review = QCourseReview.courseReview;

        OrderSpecifier<?> createdAtOrder =
                sort == SortType.LATEST ? review.createdAt.desc() : review.createdAt.asc();

        OrderSpecifier<?> idOrder =
                sort == SortType.LATEST ? review.id.desc() : review.id.asc();

        List<CourseReview> reviews = queryFactory
                .selectFrom(review)
                .join(review.user).fetchJoin()
                .join(review.course).fetchJoin()
                .where(
                        review.user.eq(user),
                        review.course.deletedAt.isNull(),
                        keywordCondition(review.content, keyword),
                        cursorCondition(
                                review.createdAt,
                                review.id,
                                cursorCreatedAt,
                                cursorId,
                                sort
                        )
                )
                .orderBy(createdAtOrder, idOrder)
                .limit(size + 1)
                .fetch();

        boolean hasNext = reviews.size() > size;

        if (hasNext) {
            reviews.remove(size.intValue());
        }

        Map<Long, List<String>> hashtagMap = getCourseHashtagMapFromCourses(
                reviews.stream()
                        .map(CourseReview::getCourse)
                        .distinct()
                        .toList()
        );

        List<UserResDTO.MyPostResponse> items = reviews.stream()
                .map(r -> {
                    Course course = r.getCourse();
                    UserResDTO.MyCourseResponse courseResponse = CourseConverter.toMyCourseResponse(
                            course,
                            s3Service.getImageUrl(course.getThumbnailKey()),
                            hashtagMap.getOrDefault(course.getId(), List.of())
                    );
                    UserResDTO.MyReviewResponse reviewResponse = CourseConverter.toMyReviewResponse(
                            r,
                            s3Service.getImageUrl(r.getUser().getProfileImage())
                    );

                    return CourseConverter.toMyPostResponse(courseResponse, reviewResponse, null);
                })
                .toList();

        Object nextCursorValue = null;
        Long nextCursorId = null;

        if (!reviews.isEmpty()) {
            CourseReview last = reviews.get(reviews.size() - 1);
            nextCursorValue = last.getCreatedAt();
            nextCursorId = last.getId();
        }

        return CursorResponse.of(
                items,
                nextCursorValue,
                nextCursorId,
                hasNext
        );


    }

    private CursorResponse<BusinessPromotionResponse.MySummary> getMyPromotions(
            User user,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size
    ) {
        QBusinessPromotion promotion =
                QBusinessPromotion.businessPromotion;

        OrderSpecifier<?> createdAtOrder =
                sort == SortType.LATEST
                        ? promotion.createdAt.desc()
                        : promotion.createdAt.asc();

        OrderSpecifier<?> idOrder =
                sort == SortType.LATEST
                        ? promotion.id.desc()
                        : promotion.id.asc();

        List<BusinessPromotion> promotions = queryFactory
                .selectFrom(promotion)
                .join(promotion.place).fetchJoin()
                .where(
                        promotion.user.eq(user),
                        promotion.status.eq(PromotionStatus.ACTIVE),
                        keywordCondition(promotion.place.name, keyword),
                        cursorCondition(
                                promotion.createdAt,
                                promotion.id,
                                cursorCreatedAt,
                                cursorId,
                                sort
                        )
                )
                .orderBy(createdAtOrder, idOrder)
                .limit(size + 1)
                .fetch();

        boolean hasNext = promotions.size() > size;

        if (hasNext) {
            promotions.remove(size.intValue());
        }

        List<Long> promotionIds = promotions.stream()
                .map(BusinessPromotion::getId)
                .toList();

        Map<Long, BusinessPromotionImage> thumbnailImageMap =
                promotionIds.isEmpty()
                        ? Map.of()
                        : businessPromotionImageRepository
                        .findAllByPromotion_IdInAndSortOrder(
                                promotionIds,
                                1
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                image -> image.getPromotion().getId(),
                                image -> image
                        ));

        Map<Long, List<String>> hashtagMap =
                promotionIds.isEmpty()
                        ? Map.of()
                        : businessPromotionHashtagRepository
                        .findAllByPromotion_IdInOrderByHashtag_IdAsc(
                                promotionIds
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                promotionHashtag ->
                                        promotionHashtag
                                                .getPromotion()
                                                .getId(),
                                Collectors.mapping(
                                        promotionHashtag ->
                                                promotionHashtag
                                                        .getHashtag()
                                                        .getHashtagName(),
                                        Collectors.toList()
                                )
                        ));

        List<Long> placeIds = promotions.stream()
                .map(promotionItem -> promotionItem.getPlace().getId())
                .distinct()
                .toList();

        Map<Long, Long> likeCountMap =
                placeIds.isEmpty()
                        ? Map.of()
                        : placeLikeRepository.countByPlaceIds(placeIds)
                        .stream()
                        .collect(Collectors.toMap(
                                PlaceLikeRepository.PlaceLikeCount::getPlaceId,
                                PlaceLikeRepository.PlaceLikeCount::getLikeCount
                        ));

        List<BusinessPromotionResponse.MySummary> items =
                promotions.stream()
                        .map(promotionItem -> {
                            BusinessPromotionImage thumbnailImage =
                                    thumbnailImageMap.get(
                                            promotionItem.getId()
                                    );

                            String thumbnailImageUrl =
                                    thumbnailImage == null
                                            ? null
                                            : s3Service.getImageUrl(
                                            thumbnailImage.getImageKey()
                                    );

                            long likeCount =
                                    likeCountMap.getOrDefault(
                                            promotionItem.getPlace().getId(),
                                            0L
                                    );

                            List<String> hashtags =
                                    hashtagMap.getOrDefault(
                                            promotionItem.getId(),
                                            List.of()
                                    );

                            return BusinessPromotionConverter
                                    .toMySummaryResponse(
                                            promotionItem,
                                            thumbnailImageUrl,
                                            hashtags,
                                            likeCount
                                    );
                        })
                        .toList();

        Object nextCursorValue = null;
        Long nextCursorId = null;

        if (!items.isEmpty()) {
            BusinessPromotionResponse.MySummary last =
                    items.get(items.size() - 1);

            nextCursorValue = last.createdAt();
            nextCursorId = last.promotionId();
        }

        return CursorResponse.of(
                items,
                nextCursorValue,
                nextCursorId,
                hasNext
        );
    }

    private CursorResponse<UserResDTO.MyPostResponse> getAllMyPosts(
            User user,
            String keyword,
            SortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size
    ) {

        CursorResponse<UserResDTO.MyCourseResponse> courseResponse =
                getMyCourses(user, keyword, sort, cursorCreatedAt, cursorId, size);

        CursorResponse<UserResDTO.MyPostResponse> reviewResponse =
                getMyReviews(user, keyword, sort, cursorCreatedAt, cursorId, size);

        CursorResponse<BusinessPromotionResponse.MySummary> promotionResponse =
                getMyPromotions(
                        user,
                        keyword,
                        sort,
                        cursorCreatedAt,
                        cursorId,
                        size
                );

        List<MyPostItem> items = new ArrayList<>();

        courseResponse.getItems().forEach(course ->
                items.add(
                        new MyPostItem(
                                course.createdAt(),
                                course.id(),
                                CourseConverter.toMyPostResponse(course, null, null)
                        )
                )
        );

        reviewResponse.getItems().forEach(reviewPost ->
                items.add(
                        new MyPostItem(
                                reviewPost.review().createdAt(),
                                reviewPost.review().reviewId(),
                                reviewPost
                        )
                )
        );

        promotionResponse.getItems().forEach(promotion ->
                items.add(
                        new MyPostItem(
                                promotion.createdAt(),
                                promotion.promotionId(),
                                CourseConverter.toMyPostResponse(
                                        null,
                                        null,
                                        promotion
                                )
                        )
                )
        );

        Comparator<MyPostItem> comparator =
                Comparator.comparing(MyPostItem::createdAt)
                        .thenComparing(MyPostItem::id);

        if (sort == SortType.LATEST) {
            comparator = comparator.reversed();
        }

        items.sort(comparator);

        boolean hasNext =
                items.size() > size
                        || courseResponse.isHasNext()
                        || reviewResponse.isHasNext()
                        || promotionResponse.isHasNext();

        List<MyPostItem> resultItems = hasNext
                ? new ArrayList<>(items.subList(0, size))
                : items;

        LocalDateTime nextCreatedAt = null;
        Long nextCursorId = null;

        if (!resultItems.isEmpty()) {
            MyPostItem last = resultItems.get(resultItems.size() - 1);
            nextCreatedAt = last.createdAt();
            nextCursorId = last.id();
        }

        return CursorResponse.of(
                resultItems.stream()
                        .map(MyPostItem::response)
                        .toList(),
                nextCreatedAt,
                nextCursorId,
                hasNext
        );
    }




    private record MyPostItem(
            LocalDateTime createdAt,
            Long id,
            UserResDTO.MyPostResponse response
    ) {}

    @Override
    @Transactional
    public UserResDTO.UpdateProfile updateMyProfile(Long userId, UserReqDTO.UpdateProfile request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        Region region = null;
        if (request.regionId() != null) {
            region = regionRepository.findById(request.regionId())
                    .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));
        }

        String profileImage = null;
        if (StringUtils.hasText(request.profileImageUrl())) {
            profileImage = fileService.moveToDirectory(
                    request.profileImageUrl(),
                    ImageDirectory.PROFILE
            );
        }

        user.updateProfile(
                request.nickname(),
                request.birthYear(),
                region,
                profileImage
        );

        return new UserResDTO.UpdateProfile(user.getId());
    }

}
