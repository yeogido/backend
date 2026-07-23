package com.yeogido.backend.domain.user.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.content.entity.ContentLike;
import com.yeogido.backend.domain.content.entity.QContentLike;
import com.yeogido.backend.domain.content.repository.ContentHashtagRepository;
import com.yeogido.backend.domain.content.repository.ContentLikeRepository;
import com.yeogido.backend.domain.course.entity.CourseLike;
import com.yeogido.backend.domain.course.entity.QCourseLike;
import com.yeogido.backend.domain.course.repository.CourseHashtagRepository;
import com.yeogido.backend.domain.course.repository.CourseLikeRepository;
import com.yeogido.backend.domain.place.entity.PlaceLike;
import com.yeogido.backend.domain.place.entity.QPlaceLike;
import com.yeogido.backend.domain.place.repository.PlaceLikeRepository;
import com.yeogido.backend.domain.user.converter.UserConverter;
import com.yeogido.backend.domain.user.dto.UserResDTO;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.domain.user.enums.LikeCategory;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
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

    private final JPAQueryFactory queryFactory;

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<UserResDTO.LikedResponse> getLikedList(
            Long userId,
            LikeCategory category,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            Integer size
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        switch (category) {
            case COURSE:{
                List<CourseLike> likes =
                        getCourseLikes(user, cursorCreatedAt, cursorId, size);

                Map<Long, List<String>> hashtagMap =
                        getCourseHashtagMap(likes);

                return toCursorResponse(
                        likes,
                        like -> UserConverter.toLikedResponse(
                                like,
                                hashtagMap.getOrDefault(
                                        like.getCourse().getId(),
                                        List.of()
                                )
                        ),
                        CourseLike::getCreatedAt,
                        CourseLike::getId,
                        size
                );
            }
            case CONTENT:{
                List<ContentLike> likes =
                        getContentLikes(user, cursorCreatedAt, cursorId, size);

                Map<Long, List<String>> hashtagMap =
                        getContentHashtagMap(likes);

                return toCursorResponse(
                        likes,
                        like -> UserConverter.toLikedResponse(
                                like,
                                hashtagMap.getOrDefault(
                                        like.getContent().getId(),
                                        List.of()
                                )
                        ),
                        ContentLike::getCreatedAt,
                        ContentLike::getId,
                        size
                );
            }
            case PLACE:
                return toCursorResponse(
                        getPlaceLikes(user, cursorCreatedAt, cursorId, size),
                        UserConverter::toLikedResponse,
                        PlaceLike::getCreatedAt,
                        PlaceLike::getId,
                        size
                );
            case ALL:
                return getAllLikes(user, cursorCreatedAt, cursorId, size);
        }
        throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
    }


    private List<CourseLike> getCourseLikes(
            User user,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        QCourseLike courseLike = QCourseLike.courseLike;

        return queryFactory
                .selectFrom(courseLike)
                .join(courseLike.course).fetchJoin()
                .leftJoin(courseLike.course.region).fetchJoin()
                .where(
                        courseLike.user.eq(user),
                        cursorCondition(courseLike.createdAt,
                                courseLike.id,
                                cursorCreatedAt,
                                cursorId)
                )
                .orderBy(
                        courseLike.createdAt.desc(),
                        courseLike.id.desc()
                )
                .limit(size + 1)
                .fetch();

    }


    private List<ContentLike> getContentLikes(
            User user,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        QContentLike contentLike = QContentLike.contentLike;

        return queryFactory
                .selectFrom(contentLike)
                .join(contentLike.content).fetchJoin()
                .leftJoin(contentLike.content.place).fetchJoin()
                .where(
                        contentLike.user.eq(user),
                        cursorCondition(contentLike.createdAt,
                                contentLike.id,
                                cursorCreatedAt,
                                cursorId)
                )
                .orderBy(
                        contentLike.createdAt.desc(),
                        contentLike.id.desc()
                )
                .limit(size + 1)
                .fetch();

    }


    private List<PlaceLike> getPlaceLikes(
            User user,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        QPlaceLike placeLike = QPlaceLike.placeLike;

        return queryFactory
                .selectFrom(placeLike)
                .join(placeLike.place).fetchJoin()
                .leftJoin(placeLike.place.region).fetchJoin()
                .where(
                        placeLike.user.eq(user),
                        cursorCondition(placeLike.createdAt,
                               placeLike.id,
                                cursorCreatedAt,
                                cursorId)
                )
                .orderBy(
                        placeLike.createdAt.desc(),
                        placeLike.id.desc()
                )
                .limit(size + 1)
                .fetch();

    }

    private CursorResponse<UserResDTO.LikedResponse> getAllLikes(
            User user,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int size
    ) {
        List<CourseLike> courseLikes =
                getCourseLikes(user, cursorCreatedAt, cursorId, size);

        List<ContentLike> contentLikes =
                getContentLikes(user, cursorCreatedAt, cursorId, size);

        List<PlaceLike> placeLikes =
                getPlaceLikes(user, cursorCreatedAt, cursorId, size);

        // course hashtag 한번 조회
        Map<Long, List<String>> courseHashtagMap =
                courseHashtagRepository.findByCourseIdIn(
                                courseLikes.stream()
                                        .map(like -> like.getCourse().getId())
                                        .toList()
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                ch -> ch.getCourse().getId(),
                                Collectors.mapping(
                                        ch -> ch.getHashtag().getHashtagName(),
                                        Collectors.toList()
                                )
                        ));

        // content hashtag 한번 조회
        Map<Long, List<String>> contentHashtagMap =
                contentHashtagRepository.findByContentIdIn(
                                contentLikes.stream()
                                        .map(like -> like.getContent().getId())
                                        .toList()
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                ch -> ch.getContent().getId(),
                                Collectors.mapping(
                                        ch -> ch.getHashtag().getHashtagName(),
                                        Collectors.toList()
                                )
                        ));

        List<LikeItem> items = new ArrayList<>();

        items.sort(
                Comparator.comparing(LikeItem::createdAt)
                        .reversed()
                        .thenComparing(LikeItem::likeId, Comparator.reverseOrder())
        );

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


    private <T> CursorResponse<UserResDTO.LikedResponse> toCursorResponse(
            List<T> likes,
            Function<T, UserResDTO.LikedResponse> converter,
            Function<T, LocalDateTime> createdAtGetter,
            Function<T, Long> idGetter,
            int size
    ) {

        boolean hasNext = likes.size() > size;

        if (hasNext) {
            likes.remove(size);
        }

        LocalDateTime nextCreatedAt = null;
        Long nextCursorId = null;

        if (!likes.isEmpty()) {
            T last = likes.get(likes.size() - 1);
            nextCreatedAt = createdAtGetter.apply(last);
            nextCursorId = idGetter.apply(last);
        }

        List<UserResDTO.LikedResponse> result =
                likes.stream()
                        .map(converter)
                        .toList();

        return CursorResponse.of(
                result,
                nextCreatedAt,
                nextCursorId,
                hasNext
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
            Long cursorId
    ) {

        if (cursorCreatedAt == null || cursorId == null) {
            return null;
        }

        return createdAt.lt(cursorCreatedAt)
                .or(
                        createdAt.eq(cursorCreatedAt)
                                .and(id.lt(cursorId))
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
