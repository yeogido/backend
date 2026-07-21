package com.yeogido.backend.domain.content.service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
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
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;

import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.ContentHandler;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    @Override
    public CursorResponse<ContentResDTO.ContentInfo> getContents(ContentReqDTO.ContentListReq request){

        BooleanBuilder builder = new BooleanBuilder();

        int size = request.size() == null ? DEFAULT_PAGE_SIZE : request.size();


        Long cursorLikeCount = null;
        LocalDate cursorEndDate = null;
        String cursorValue = request.cursorValue();
        Long cursorId = request.cursorId();
        Double cursorDistance = null;

        Object nextCursorValue = null;
        Long nextCursorId = null;

        if (request.regionId() != null
                && request.sort() != ContentSort.DISTANCE) {
            builder.and(qContent.place.region.id.eq(request.regionId()));
        }

        if (request.category() != null) {
            builder.and(qContent.category.eq(request.category()));
        }

        if (request.keyword() != null && !request.keyword().isBlank()) {
            builder.and(qContent.title.containsIgnoreCase(request.keyword()));
        }

        //TODO : 검색 로직 구현

        List<ContentResDTO.ContentInfo> result = new ArrayList<>();
        List<Content> contents=new ArrayList<>();
        boolean hasNext = false;


        // 정렬 기준
        switch (request.sort()) {

            case LIKE ->{

                if (request.cursorValue() != null) {
                    cursorLikeCount = Long.valueOf(request.cursorValue());
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

                result = tuples.stream()
                        .map(tuple -> ContentConverter.toContentInfo(
                                tuple,
                                qContent,
                                likeCountExpression
                        ))
                        .toList();
            }

            case DEADLINE -> {

                if (cursorValue != null) {
                    cursorEndDate = LocalDate.parse(cursorValue);
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

                Map<Long, Long> likeCountMap = getLikeCountMap(contents);

                result = contents.stream()
                        .map(content -> ContentConverter.toContentInfo(
                                content,
                                likeCountMap.getOrDefault(content.getId(), 0L)
                        ))
                        .toList();

            }
            case DISTANCE ->{

                if (cursorValue != null) {
                    cursorDistance = Double.valueOf(cursorValue);
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

                Map<Long, Long> likeCountMap = getLikeCountMap(contents);

                result = contents.stream()
                        .map(content -> ContentConverter.toContentInfo(
                                content,
                                likeCountMap.getOrDefault(content.getId(), 0L)
                        ))
                        .toList();

            }

            case RECOMMEND -> {
                contents = getRecommendedContents(builder,size);

                //TODO : 추천순 nextCursor 구현
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
            Tuple center = queryFactory
                    .select(avgLatitude, avgLongitude)
                    .from(qPlace)
                    .where(qPlace.region.id.eq(request.regionId()))
                    .fetchOne();

            if (center == null
                    || center.get(avgLatitude) == null
                    || center.get(avgLongitude) == null) {
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
            }

            baseLatitude = center.get(avgLatitude);
            baseLongitude = center.get(avgLongitude);
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


    // 추천순
    private List<Content> getRecommendedContents(
            BooleanBuilder builder,
            int size
    ) {

        // TODO : 추천순 로직 구현

        return queryFactory
                .selectFrom(qContent)
                .where(builder)
                .orderBy(
                        qContent.createdAt.desc(),
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




    @Override
    public ContentResDTO.ContentDetailRes getContentDetail(Long contentId){

        Content content = contentRepository.findById(contentId)
                .orElseThrow(()-> new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND));

        //TODO : 로그인 유저 가져오기
        User currentUser = null;

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


        List<ContentResDTO.CourseInfo> courses =
                courseItemRepository.findByContentOrderByOrderNoAsc(content)
                        .stream()
                        .map(courseItem -> {

                            Course course = courseItem.getCourse();

                            boolean courseLiked = false;

                            if (currentUser != null) {
                                courseLiked =
                                        courseLikeRepository.existsByCourseAndUser(course, currentUser);
                            }


                            return ContentConverter.toCourseInfo(course, courseLiked);

                        })
                        .toList();


        return ContentConverter.toContentDetailRes(
                content,
                hashtags,
                liked,
                placeInfo,
                courses
        );

    }


    @Override
    @Transactional
    public ContentResDTO.ContentCreateRes createContent(ContentReqDTO.ContentCreateReq request) {

        Place place = placeService.getOrCreatePlace(request.place());

        Content content = ContentConverter.toContent(request, place);

        Content savedContent = contentRepository.save(content);

        if (request.hashtagIds() != null && !request.hashtagIds().isEmpty()) {

            for (Long hashtagId : request.hashtagIds()) {

                Hashtag hashtag = hashtagRepository.findById(hashtagId)
                        .orElseThrow(() -> new GeneralException(HashtagErrorCode.HASHTAG_NOT_FOUND));

                ContentHashtag contentHashtag = ContentHashtag.builder()
                        .content(savedContent)
                        .hashtag(hashtag)
                        .build();

                contentHashtagRepository.save(contentHashtag);
            }
        }

        return new ContentResDTO.ContentCreateRes(
                savedContent.getId()
        );
    }

    @Transactional
    @Override
    public ContentResDTO.ContentUpdateRes updateContent(Long contentId, ContentReqDTO.ContentCreateReq request){
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND));

        Place place = placeService.getOrCreatePlace(request.place());

        content.update(
                place,
                request.place().externalPlaceId(),
                request.title(),
                request.description(),
                request.thumbnailImageKey(),
                request.startDate(),
                request.endDate(),
                request.contactPhone(),
                request.officialUrl(),
                ContentCategory.valueOf(request.category()),
                PlaceSource.valueOf(request.place().source()) == PlaceSource.KAKAO
                        ? ContentSource.ADMIN
                        : ContentSource.TOUR_API
        );

        contentHashtagRepository.deleteByContentId(contentId);

        if (request.hashtagIds() != null && !request.hashtagIds().isEmpty()) {

            for (Long hashtagId : request.hashtagIds()) {

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

        return new ContentResDTO.ContentUpdateRes(content.getId());
    }

    @Override
    public ContentResDTO.ContentLikeRes likeContent(Long contentId, Long userId){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND));

        if (!contentLikeRepository.existsByUserAndContent(user, content)) {
            ContentLike contentLike = ContentLike.builder()
                    .user(user)
                    .content(content)
                    .build();

            contentLikeRepository.save(contentLike);
        }

        Long likeCount = contentLikeRepository.countByContent(content);

        return new ContentResDTO.ContentLikeRes(
                true,
                likeCount
        );
    }


    @Override
    @Transactional
    public ContentResDTO.ContentLikeRes unlikeContent(Long contentId, Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new GeneralException(ContentErrorCode.CONTENT_NOT_FOUND));

        contentLikeRepository.findByUserAndContent(user, content)
                .ifPresent(contentLikeRepository::delete);

        Long likeCount = contentLikeRepository.countByContent(content);

        return new ContentResDTO.ContentLikeRes(
                false,
                likeCount
        );
    }
}
