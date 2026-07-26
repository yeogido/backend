package com.yeogido.backend.domain.user.service;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.ContentLike;
import com.yeogido.backend.domain.content.entity.QContentLike;
import com.yeogido.backend.domain.content.repository.ContentHashtagRepository;
import com.yeogido.backend.domain.content.repository.ContentLikeRepository;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.entity.CourseLike;
import com.yeogido.backend.domain.course.entity.QCourseLike;
import com.yeogido.backend.domain.course.repository.CourseHashtagRepository;
import com.yeogido.backend.domain.course.repository.CourseLikeRepository;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.entity.PlaceLike;
import com.yeogido.backend.domain.place.entity.QPlaceLike;
import com.yeogido.backend.domain.place.repository.PlaceLikeRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.user.converter.UserConverter;
import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.LikeSortType;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.domain.user.enums.LikeCategory;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.global.util.DistanceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;
    private final CourseLikeRepository courseLikeRepository;
    private final ContentLikeRepository contentLikeRepository;
    private final PlaceLikeRepository placeLikeRepository;
    private final CourseHashtagRepository courseHashtagRepository;
    private final ContentHashtagRepository contentHashtagRepository;
    private final S3Service s3Service;

    private final JPAQueryFactory queryFactory;

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<UserResDTO.LikedResponse> getLikedList(
            Long userId,
            LikeCategory category,
            LikeSortType sort,
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
                        getCourseLikes(user, sort, cursorCreatedAt, cursorId, size);

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
                        getContentLikes(user, sort, cursorCreatedAt, cursorId, size);

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
                        getPlaceLikes(user, sort, cursorCreatedAt, cursorId, size);

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

                List<UserResDTO.LikedResponse> result =
                        likes.stream()
                                .map(like -> UserConverter.toLikedResponse(
                                        like,
                                        resolvedLatitude,
                                        resolvedLongitude
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
                return getAllLikes(user, sort, cursorCreatedAt, cursorId, size, resolvedLatitude, resolvedLongitude);
        }
        throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }


    private List<CourseLike> getCourseLikes(
            User user,
            LikeSortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        QCourseLike courseLike = QCourseLike.courseLike;

        OrderSpecifier<?> createdAtOrder =
                sort == LikeSortType.LATEST
                        ? courseLike.createdAt.desc()
                        : courseLike.createdAt.asc();

        OrderSpecifier<?> idOrder =
                sort == LikeSortType.LATEST
                        ? courseLike.id.desc()
                        : courseLike.id.asc();

        return queryFactory
                .selectFrom(courseLike)
                .join(courseLike.course).fetchJoin()
                .leftJoin(courseLike.course.region).fetchJoin()
                .where(
                        courseLike.user.eq(user),
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
            LikeSortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        QContentLike contentLike = QContentLike.contentLike;

        OrderSpecifier<?> createdAtOrder =
                sort == LikeSortType.LATEST
                        ? contentLike.createdAt.desc()
                        : contentLike.createdAt.asc();

        OrderSpecifier<?> idOrder =
                sort == LikeSortType.LATEST
                        ? contentLike.id.desc()
                        : contentLike.id.asc();

        return queryFactory
                .selectFrom(contentLike)
                .join(contentLike.content).fetchJoin()
                .leftJoin(contentLike.content.place).fetchJoin()
                .where(
                        contentLike.user.eq(user),
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
            LikeSortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        QPlaceLike placeLike = QPlaceLike.placeLike;

        OrderSpecifier<?> createdAtOrder =
                sort == LikeSortType.LATEST
                        ? placeLike.createdAt.desc()
                        : placeLike.createdAt.asc();

        OrderSpecifier<?> idOrder =
                sort == LikeSortType.LATEST
                        ? placeLike.id.desc()
                        : placeLike.id.asc();


        return queryFactory
                .selectFrom(placeLike)
                .join(placeLike.place).fetchJoin()
                .leftJoin(placeLike.place.region).fetchJoin()
                .where(
                        placeLike.user.eq(user),
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
            LikeSortType sort,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size,
            Double latitude,
            Double longitude
    ) {
        // 카테고리별 좋아요 목록 조회
        List<CourseLike> courseLikes =
                getCourseLikes(user, sort, cursorCreatedAt, cursorId, size);

        List<ContentLike> contentLikes =
                getContentLikes(user, sort, cursorCreatedAt, cursorId, size);

        List<PlaceLike> placeLikes =
                getPlaceLikes(user, sort, cursorCreatedAt, cursorId, size);

        // 해시태그 조회
        Map<Long, List<String>> courseHashtagMap =
                getCourseHashtagMap(courseLikes);

        Map<Long, List<String>> contentHashtagMap =
                getContentHashtagMap(contentLikes);


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
                items.add(toPlaceLikeItem(like,latitude, longitude))
        );

        Comparator<LikeItem> comparator =
                Comparator.comparing(LikeItem::createdAt)
                        .thenComparing(LikeItem::likeId);

        if (sort == LikeSortType.LATEST) {
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
            Double longitude
    ) {
        return new LikeItem(
                like.getCreatedAt(),
                like.getId(),
                UserConverter.toLikedResponse(
                        like,
                        latitude,
                        longitude
                )
        );
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


    private BooleanExpression cursorCondition(
            DateTimePath<LocalDateTime> createdAt,
            NumberPath<Long> id,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            LikeSortType sort
    ) {

        if (cursorCreatedAt == null || cursorId == null) {
            return null;
        }

        if (sort == LikeSortType.LATEST) {
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

    @Override
    @Transactional(readOnly = true)
    public UserResDTO.Profile getMyPage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        return UserConverter.toProfile(user);
    }

}
