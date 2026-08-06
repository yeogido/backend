package com.yeogido.backend.domain.content.service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.content.converter.ContentConverter;
import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentLike;
import com.yeogido.backend.domain.content.entity.ContentHashtag;
import com.yeogido.backend.domain.content.entity.QContent;
import com.yeogido.backend.domain.content.entity.QContentLike;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentSort;

import com.yeogido.backend.domain.content.enums.ContentSource;
import com.yeogido.backend.domain.content.exception.ContentErrorCode;
import com.yeogido.backend.domain.content.repository.ContentHashtagRepository;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.course.entity.CourseItem;
import com.yeogido.backend.domain.file.enums.ImageDirectory;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.hashtag.entity.Hashtag;
import com.yeogido.backend.domain.hashtag.exception.HashtagErrorCode;
import com.yeogido.backend.domain.hashtag.repository.HashtagRepository;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.entity.QPlace;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.service.PlaceService;
import com.yeogido.backend.domain.content.repository.ContentLikeRepository;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.repository.CourseItemRepository;
import com.yeogido.backend.domain.course.repository.CourseLikeRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;

import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.ErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jdk.jshell.spi.ExecutionControl;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.ContentHandler;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentServiceImpl implements ContentService{

    private final JPAQueryFactory queryFactory;
    private static final int DEFAULT_PAGE_SIZE = 6;
    private final QContent qContent = QContent.content;
    private final QPlace qPlace = QPlace.place;
    private final QContentLike qContentLike = QContentLike.contentLike;
    private final NumberExpression<Long> likeCountExpression = qContentLike.id.count();
    private final ContentRepository contentRepository;
    private final PlaceService placeService;
    private final ContentHashtagRepository contentHashtagRepository;
    private final HashtagRepository hashtagRepository;

    private final ContentLikeRepository contentLikeRepository;
    private final CourseItemRepository courseItemRepository;
    private final CourseLikeRepository courseLikeRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;

    private final FileService fileService;
    private final S3Service s3Service;


    @Override
    public CursorResponse<ContentResDTO.ContentInfo> getContents(ContentReqDTO.ContentListReq request){

        BooleanBuilder builder = new BooleanBuilder();

        int size = request.size() == null ? DEFAULT_PAGE_SIZE : request.size();


        Long cursorLikeCount = null;
        LocalDate cursorEndDate = null;
        String cursorValue = request.cursorValue();
        Long cursorId = request.cursorId();

        boolean hasCursorValue = cursorValue != null;
        boolean hasCursorId = cursorId != null;

        if (hasCursorValue != hasCursorId) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }

        Double cursorDistance = null;

        Object nextCursorValue = null;
        Long nextCursorId = null;

        Integer cursorRecommendPriority = null;
        LocalDateTime nextCursorCreatedAt = null;

        if (request.regionId() != null
                && request.sort() != ContentSort.DISTANCE) {
            Region region = regionRepository.findById(request.regionId())
                    .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));

            builder.and(qContent.place.region.id.in(getRegionIds(region)));
        }
        if (request.category() != null) {
            builder.and(qContent.category.eq(request.category()));
        }

        if (request.keyword() != null && !request.keyword().isBlank()) {
            builder.and(
                    qContent.title.containsIgnoreCase(request.keyword())
                            .or(qContent.place.name.containsIgnoreCase(request.keyword()))
                            .or(qContent.place.region.fullName.containsIgnoreCase(request.keyword()))
            );
        }

        List<ContentResDTO.ContentInfo> result = new ArrayList<>();
        List<Content> contents=new ArrayList<>();
        boolean hasNext = false;

        ContentSort sort =
                request.sort() == null ? ContentSort.RECOMMEND : request.sort();

        // 정렬 기준
        switch (sort) {

            case LIKE ->{

                if (cursorValue != null) {
                    try {
                        cursorLikeCount = Long.valueOf(cursorValue);
                    } catch (NumberFormatException e) {
                        throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
                    }
                }

                List<Tuple> tuples = getLikeContents(
                        builder,
                        size,
                        cursorLikeCount,
                        request.cursorId()
                );

                hasNext = tuples.size() > size;

                if (hasNext) {
                    tuples.remove(tuples.size() - 1);
                }

                if (!tuples.isEmpty()) {

                    Tuple lastTuple = tuples.get(tuples.size() - 1);

                    nextCursorValue = lastTuple.get(likeCountExpression);
                    nextCursorId = lastTuple.get(qContent).getId();
                }

                contents = tuples.stream()
                        .map(tuple -> tuple.get(qContent))
                        .toList();

                List<ContentHashtag> contentHashtags =
                        contentHashtagRepository.findAllByContentIn(contents);

                Map<Long, List<String>> hashtagMap =
                        contentHashtags.stream()
                                .collect(Collectors.groupingBy(
                                        ch -> ch.getContent().getId(),
                                        Collectors.mapping(
                                                ch -> ch.getHashtag().getHashtagName(),
                                                Collectors.toList()
                                        )
                                ));

                result = tuples.stream()
                        .map(tuple -> {
                            Content content = tuple.get(qContent);

                            String imageUrl =
                                    s3Service.getImageUrl(content.getThumbnailImage());

                            return ContentConverter.toContentInfo(
                                    tuple,
                                    qContent,
                                    likeCountExpression,
                                    hashtagMap,
                                    imageUrl
                            );
                        })
                        .toList();
            }

            case DEADLINE -> {

                if (cursorValue != null) {
                    try {
                        cursorEndDate = LocalDate.parse(cursorValue);
                    } catch (DateTimeParseException e) {
                        throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
                    }
                }

                contents = getDeadlineContents(builder, size, cursorId, cursorEndDate);

                hasNext = contents.size() > size;

                if (hasNext) {
                    contents.remove(contents.size() - 1);
                }

                if (!contents.isEmpty()) {
                    Content last = contents.get(contents.size() - 1);

                    nextCursorValue = last.getEndDate();
                    nextCursorId = last.getId();
                }

                result = createContentInfos(contents);

            }
            case DISTANCE ->{

                if (cursorValue != null) {
                    try {
                        cursorDistance = Double.valueOf(cursorValue);

                        if (!Double.isFinite(cursorDistance)) {
                            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
                        }
                    } catch (NumberFormatException e) {
                        throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
                    }
                }

                nextCursorValue = getDistanceContents(
                        request,
                        builder,
                        size,
                        cursorDistance,
                        cursorId,
                        contents
                );

                hasNext = contents.size() > size;

                if (hasNext) {
                    contents.remove(contents.size() - 1);
                }

                if (!contents.isEmpty()) {
                    nextCursorId = contents.get(contents.size() - 1).getId();
                }

                result = createContentInfos(contents);

            }

            case RECOMMEND -> {
                if (cursorValue != null) {
                    try {
                        cursorRecommendPriority = Integer.valueOf(cursorValue);
                    } catch (NumberFormatException e) {
                        throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
                    }
                }


                contents = getRecommendedContents(
                        builder,
                        size,
                        cursorRecommendPriority,
                        cursorId
                );

                hasNext = contents.size() > size;

                if (hasNext) {
                    contents.remove(contents.size() - 1);
                }

                if (!contents.isEmpty()) {
                    Content last = contents.get(contents.size() - 1);

                    nextCursorValue = last.getRecommendPriority();
                    nextCursorCreatedAt = last.getCreatedAt();
                    nextCursorId = last.getId();
                }

                List<ContentHashtag> contentHashtags =
                        contentHashtagRepository.findAllByContentIn(contents);

                result = createContentInfos(contents);
            }
        }


        return CursorResponse.of(
                result,
                nextCursorValue,
                nextCursorId,
                hasNext
        );
    }


    // 저장순(좋아요 많은 순)
    private List<Tuple> getLikeContents(
            BooleanBuilder builder,
            int size,
            Long cursorLikeCount,
            Long cursorId
    ) {

        var query = queryFactory
                .select(qContent, likeCountExpression)
                .from(qContent)
                .leftJoin(qContentLike)
                .on(qContentLike.content.eq(qContent))
                .where(builder)
                .groupBy(qContent.id);

        if (cursorLikeCount != null && cursorId != null) {
            query.having(
                    likeCountExpression.lt(cursorLikeCount)
                            .or(likeCountExpression.eq(cursorLikeCount)
                                    .and(qContent.id.gt(cursorId))
                            )
            );
        }

        return query
                .orderBy(likeCountExpression.desc(), qContent.id.asc())
                .limit(size + 1)
                .fetch();

    }

    // 종료 임박순
    private List<Content> getDeadlineContents(
            BooleanBuilder builder,
            int size,
            Long cursorId,
            LocalDate cursorEndDate
    ) {

        builder.and(qContent.endDate.goe(LocalDate.now()));

        if (cursorEndDate != null && cursorId != null) {
            builder.and(
                    qContent.endDate.gt(cursorEndDate)
                            .or(
                                    qContent.endDate.eq(cursorEndDate)
                                            .and(qContent.id.gt(cursorId))
                            )
            );
        }

        return queryFactory
                .selectFrom(qContent)
                .where(builder)
                .orderBy(qContent.endDate.asc(), qContent.id.asc())
                .limit(size+1)
                .fetch();

    }


    // 거리순
    private Double getDistanceContents(
            ContentReqDTO.ContentListReq request,
            BooleanBuilder builder,
            int size,
            Double cursorDistance,
            Long cursorId,
            List<Content> contents
    ) {

        NumberExpression<Double> avgLatitude = qPlace.latitude.avg();
        NumberExpression<Double> avgLongitude = qPlace.longitude.avg();

        double baseLatitude;
        double baseLongitude;

        //GPS 허용 시
        if(request.latitude() != null && request.longitude() != null){
            baseLatitude = request.latitude();
            baseLongitude = request.longitude();

        }else {
            if (request.regionId() == null) {
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
            }

            //GPS 미허용 시 선택한 지역 중심 기준
            Region region = regionRepository.findById(request.regionId())
                    .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_PARAMETER));

            if (region.getLatitude() == null || region.getLongitude() == null) {
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
            }

            baseLatitude = region.getLatitude().doubleValue();
            baseLongitude = region.getLongitude().doubleValue();

            builder.and(qContent.place.region.id.in(getRegionIds(region)));
        }

        NumberExpression<Double> distance =
                createDistanceExpression(baseLatitude, baseLongitude);

        if (cursorDistance != null && cursorId != null) {
            builder.and(
                    distance.gt(cursorDistance)
                            .or(
                                    distance.eq(cursorDistance)
                                            .and(qContent.id.gt(cursorId))
                            )
            );
        }

        List<Tuple> tuples = queryFactory
                .select(qContent, distance)
                .from(qContent)
                .where(builder)
                .orderBy(distance.asc(), qContent.id.asc())
                .limit(size + 1)
                .fetch();

        contents.addAll(
                tuples.stream()
                        .map(tuple -> tuple.get(qContent))
                        .toList()
        );

        if (tuples.isEmpty()) {
            return null;
        }

        if (tuples.size() > size) {
            tuples.remove(tuples.size() - 1);
        }

        Tuple lastTuple = tuples.get(tuples.size() - 1);

        return lastTuple.get(distance);


    }

    private List<Long> getRegionIds(Region region) {
        List<Long> regionIds = new ArrayList<>();
        regionIds.add(region.getId());

        if (region.getType() == RegionType.REGION) {
            regionIds.addAll(
                    regionRepository.findByParentIdAndTypeOrderByNameAsc(
                                    region.getId(),
                                    RegionType.SUB_REGION
                            ).stream()
                            .map(Region::getId)
                            .toList()
            );
        }

        return regionIds;
    }


    // 추천순
    private List<Content> getRecommendedContents(
            BooleanBuilder builder,
            int size,
            Integer cursorPriority,
            Long cursorId
    ) {
        NumberExpression<Integer> unrecommendedOrder = new CaseBuilder()
                .when(qContent.recommendPriority.eq(0))
                .then(1)
                .otherwise(0);

        JPAQuery<Content> query = queryFactory
                .selectFrom(qContent)
                .where(builder);

        if (cursorPriority != null && cursorId != null) {
            if (cursorPriority == 0) {
                query.where(
                        qContent.recommendPriority.eq(0)
                                .and(qContent.id.gt(cursorId))
                );
            } else {
                query.where(
                        qContent.recommendPriority.gt(cursorPriority)
                                .and(qContent.recommendPriority.ne(0))
                                .or(
                                        qContent.recommendPriority.eq(cursorPriority)
                                                .and(qContent.id.gt(cursorId))
                                )
                                .or(qContent.recommendPriority.eq(0))
                );
            }
        }

        return query
                .orderBy(
                        unrecommendedOrder.asc(),
                        qContent.recommendPriority.asc(),
                        qContent.id.asc()
                )
                .limit(size + 1)
                .fetch();
    }


    //거리 계산(거리순)
    private NumberExpression<Double> createDistanceExpression(
            double baseLatitude,
            double baseLongitude
    ) {
        return Expressions.numberTemplate(
                Double.class,
                "POWER({0} - {1}, 2) + POWER({2} - {3}, 2)",
                qContent.place.latitude,
                baseLatitude,
                qContent.place.longitude,
                baseLongitude
        );
    }

    private Map<Long, Long> getLikeCountMap(List<Content> contents) {

        if (contents.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> contentIds = contents.stream()
                .map(Content::getId)
                .toList();

        List<Tuple> tuples = queryFactory
                .select(qContentLike.content.id, qContentLike.id.count())
                .from(qContentLike)
                .where(qContentLike.content.id.in(contentIds))
                .groupBy(qContentLike.content.id)
                .fetch();

        return tuples.stream()
                .collect(Collectors.toMap(
                        tuple -> tuple.get(qContentLike.content.id),
                        tuple -> tuple.get(qContentLike.id.count())
                ));
    }

    private List<ContentResDTO.ContentInfo> createContentInfos(List<Content> contents) {

        Map<Long, Long> likeCountMap = getLikeCountMap(contents);

        List<ContentHashtag> contentHashtags =
                contentHashtagRepository.findAllByContentIn(contents);

        Map<Long, List<String>> hashtagMap =
                contentHashtags.stream()
                        .collect(Collectors.groupingBy(
                                ch -> ch.getContent().getId(),
                                Collectors.mapping(
                                        ch -> ch.getHashtag().getHashtagName(),
                                        Collectors.toList()
                                )
                        ));

        return contents.stream()
                .map(content -> {
                    String imageUrl =
                            s3Service.getImageUrl(content.getThumbnailImage());

                    return ContentConverter.toContentInfo(
                            content,
                            imageUrl,
                            likeCountMap.getOrDefault(content.getId(), 0L),
                            hashtagMap.getOrDefault(content.getId(), List.of())
                    );
                })
                .toList();
    }




    @Override
    public ContentResDTO.ContentDetailRes getContentDetail(Long contentId, Long userId){

        Content content = contentRepository.findById(contentId)
                .orElseThrow(()-> new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND));

        User currentUser = null;

        if (userId != null) {
            currentUser = userRepository.findById(userId)
                    .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
        }

        List<String> hashtags = contentHashtagRepository.findByContent(content)
                .stream()
                .map(ch->ch.getHashtag().getHashtagName())
                .toList();


        boolean liked = false;
        if(currentUser!=null){
            liked = contentLikeRepository.existsByContentAndUser(content,currentUser);
        }

        ContentResDTO.PlaceInfo placeInfo =
                ContentConverter.toPlaceInfo(content.getPlace());

        List<CourseItem> courseItems =
                courseItemRepository.findByContentOrderByOrderNoAsc(content);

        final Set<Long> likedCourseIds;

        if (currentUser != null && !courseItems.isEmpty()) {

            List<Long> courseIds = courseItems.stream()
                    .map(courseItem -> courseItem.getCourse().getId())
                    .toList();

            likedCourseIds = new HashSet<>(
                    courseLikeRepository.findLikedCourseIds(currentUser, courseIds)
            );

        } else {
            likedCourseIds = Collections.emptySet();
        }

        List<ContentResDTO.CourseInfo> courses =
                courseItems.stream()
                        .map(courseItem -> {

                            Course course = courseItem.getCourse();

                            boolean courseLiked =
                                    likedCourseIds.contains(course.getId());

                            String thumbnailImageUrl = s3Service.getImageUrl(course.getThumbnailKey());

                            return ContentConverter.toCourseInfo(course, thumbnailImageUrl, courseLiked);
                        })
                        .toList();

        String imageUrl = s3Service.getImageUrl(content.getThumbnailImage());

        return ContentConverter.toContentDetailRes(
                content,
                imageUrl,
                hashtags,
                liked,
                placeInfo,
                courses
        );

    }


    @Override
    @Transactional
    public ContentResDTO.ContentCreateRes createContent(ContentReqDTO.ContentCreateReq request, Long userId) {

        validateAdmin(userId);

        Place place = placeService.getOrCreatePlace(request.place());
        ContentReqDTO.ContentCreateReq movedRequest = moveContentImage(request);

        Content content = ContentConverter.toContent(movedRequest, place);

        Content savedContent = contentRepository.save(content);

        saveContentHashtags(savedContent, movedRequest.hashtagIds());

        return new ContentResDTO.ContentCreateRes(
                savedContent.getId()
        );
    }

    @Transactional
    @Override
    public ContentResDTO.ContentUpdateRes updateContent(Long contentId, ContentReqDTO.ContentCreateReq request, Long userId){

        validateAdmin(userId);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND));

        Place place = placeService.getOrCreatePlace(request.place());
        ContentReqDTO.ContentCreateReq movedRequest = moveContentImage(request);

        content.update(
                place,
                movedRequest.place().externalPlaceId(),
                movedRequest.title(),
                movedRequest.description(),
                movedRequest.thumbnailImageKey(),
                movedRequest.startDate(),
                movedRequest.endDate(),
                movedRequest.contactPhone(),
                movedRequest.officialUrl(),
                movedRequest.category(),
                movedRequest.place().source() == PlaceSource.KAKAO
                        ? ContentSource.ADMIN
                        : ContentSource.TOUR_API
        );

        contentHashtagRepository.deleteByContentId(contentId);

        saveContentHashtags(content, movedRequest.hashtagIds());

        return new ContentResDTO.ContentUpdateRes(content.getId());
    }

    private ContentReqDTO.ContentCreateReq moveContentImage(ContentReqDTO.ContentCreateReq request) {
        String thumbnailImageKey = request.thumbnailImageKey();

        if (StringUtils.hasText(thumbnailImageKey)) {
            thumbnailImageKey = fileService.moveToDirectory(thumbnailImageKey, ImageDirectory.CONTENT);
        }

        return new ContentReqDTO.ContentCreateReq(
                request.place(),
                request.title(),
                request.description(),
                request.category(),
                request.startDate(),
                request.endDate(),
                request.contactPhone(),
                request.officialUrl(),
                thumbnailImageKey,
                request.hashtagIds()
        );
    }


    @Override
    @Transactional
    public void deleteContent(Long contentId, Long userId) {

        validateAdmin(userId);

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND));

        List<CourseItem> courseItems =
                courseItemRepository.findByContentOrderByOrderNoAsc(content);

        for (CourseItem courseItem : courseItems) {

            Long courseId = courseItem.getCourse().getId();
            Integer deletedOrder = courseItem.getOrderNo();

            courseItemRepository.delete(courseItem);

            List<CourseItem> remainItems =
                    courseItemRepository.findByCourseIdOrderByOrderNoAsc(courseId);

            for (CourseItem item : remainItems) {
                if (item.getOrderNo() > deletedOrder) {
                    item.updateOrderNo(item.getOrderNo() - 1);
                }
            }
        }

        contentHashtagRepository.deleteByContent(content);
        contentLikeRepository.deleteByContent(content);
        contentRepository.delete(content);
    }


    @Override
    public ContentResDTO.ContentLikeRes likeContent(Long contentId, Long userId){
        User user = getUserOrThrow(userId);
        Content content = getContentOrThrow(contentId);

        contentLikeRepository.insertIgnore(content.getId(), user.getId());

        Long likeCount = contentLikeRepository.countByContent(content);

        return new ContentResDTO.ContentLikeRes(
                true,
                likeCount
        );
    }


    @Override
    @Transactional
    public ContentResDTO.ContentLikeRes unlikeContent(Long contentId, Long userId) {

        User user = getUserOrThrow(userId);
        Content content = getContentOrThrow(contentId);

        contentLikeRepository.findByUserAndContent(user, content)
                .ifPresent(contentLikeRepository::delete);

        Long likeCount = contentLikeRepository.countByContent(content);

        return new ContentResDTO.ContentLikeRes(
                false,
                likeCount
        );
    }

    private void validateAdmin(Long userId) {
        User user = getUserOrThrow(userId);

        if (user.getRole() != UserRole.ADMIN) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    private void saveContentHashtags(Content content, List<Long> hashtagIds) {
        if (hashtagIds == null || hashtagIds.isEmpty()) {
            return;
        }

        for (Long hashtagId : hashtagIds) {
            Hashtag hashtag = hashtagRepository.findById(hashtagId)
                    .orElseThrow(() -> new GeneralException(HashtagErrorCode.HASHTAG_NOT_FOUND));

            contentHashtagRepository.save(
                    ContentHashtag.builder()
                            .content(content)
                            .hashtag(hashtag)
                            .build()
            );
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
    }

    private Content getContentOrThrow(Long contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentResDTO.BannerRes> getBannerContents() {

        List<Content> contents =
                contentRepository.findTop5ByEndDateGreaterThanEqualOrderByEndDateAsc(LocalDate.now());

        return contents.stream()
                .map(content -> ContentConverter.toBannerRes(
                        content,
                        s3Service.getImageUrl(content.getThumbnailImage())
                ))
                .toList();
    }
}
