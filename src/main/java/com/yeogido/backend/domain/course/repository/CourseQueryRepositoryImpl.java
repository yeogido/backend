package com.yeogido.backend.domain.course.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.entity.QCourse;
import com.yeogido.backend.domain.course.entity.QCourseLike;
import com.yeogido.backend.domain.course.entity.QCourseReview;
import com.yeogido.backend.domain.course.enums.CourseSortType;
import com.yeogido.backend.domain.region.entity.QRegion;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class CourseQueryRepositoryImpl implements CourseQueryRepository {

    private static final int RECOMMEND_NULL_ORDER = 1_000_000_000;

    private final JPAQueryFactory queryFactory;

    private final QCourse course = QCourse.course;
    private final QRegion region = QRegion.region;
    private final QRegion parentRegion = new QRegion("parentRegion");
    private final QCourseLike courseLike = QCourseLike.courseLike;
    private final QCourseReview courseReview = QCourseReview.courseReview;

    public CourseQueryRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<CourseListRow> findCoursesByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            int limit
    ) {
        NumberExpression<Long> savedCount = courseLike.id.countDistinct();
        NumberExpression<Long> reviewCount = courseReview.id.countDistinct();
        NumberExpression<Double> distance = distanceExpression(location);
        NumberExpression<Integer> recommendOrder = recommendOrderExpression();

        JPAQuery<CourseListRow> query = queryFactory
                .select(Projections.constructor(
                        CourseListRow.class,
                        course.id,
                        course.thumbnailKey,
                        course.title,
                        region.name,
                        course.durationType,
                        course.transportType,
                        course.companionType,
                        course.createdAt,
                        recommendOrder,
                        savedCount,
                        reviewCount,
                        distance
                ))
                .from(course)
                .join(course.region, region);

        if (hasKeyword(request.keyword())) {
            query.leftJoin(region.parent, parentRegion);
        }

        query.leftJoin(courseLike).on(courseLike.course.eq(course))
                .leftJoin(courseReview).on(courseReview.course.eq(course))
                .where(baseCondition(request))
                .groupBy(
                        course.id,
                        course.thumbnailKey,
                        course.title,
                        region.name,
                        course.durationType,
                        course.transportType,
                        course.companionType,
                        course.createdAt,
                        course.recommendOrder,
                        region.latitude,
                        region.longitude
                )
                .orderBy(orderSpecifiers(request.sort(), savedCount, reviewCount, distance))
                .limit(limit);

        BooleanExpression cursorCondition = cursorCondition(
                request,
                recommendOrder,
                savedCount,
                reviewCount,
                distance
        );

        if (cursorCondition != null) {
            query.having(cursorCondition);
        }

        return query.fetch();
    }

    private BooleanBuilder baseCondition(CourseReqDTO.CourseListReq request) {
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(course.courseType.eq(request.courseType()));
        builder.and(course.deletedAt.isNull());
        builder.and(transportTypeEq(request));
        builder.and(durationTypeEq(request));
        builder.and(companionTypeEq(request));
        builder.and(keywordContains(request.keyword()));
        builder.and(regionCoordinateExists(request));

        return builder;
    }

    private BooleanExpression transportTypeEq(CourseReqDTO.CourseListReq request) {
        return request.transportType() == null
                ? null
                : course.transportType.eq(request.transportType());
    }

    private BooleanExpression durationTypeEq(CourseReqDTO.CourseListReq request) {
        return request.durationType() == null
                ? null
                : course.durationType.eq(request.durationType());
    }

    private BooleanExpression companionTypeEq(CourseReqDTO.CourseListReq request) {
        return request.companionType() == null
                ? null
                : course.companionType.eq(request.companionType());
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!hasKeyword(keyword)) {
            return null;
        }

        String normalizedKeyword = keyword.trim();

        return course.title.containsIgnoreCase(normalizedKeyword)
                .or(region.name.containsIgnoreCase(normalizedKeyword))
                .or(region.fullName.containsIgnoreCase(normalizedKeyword))
                .or(parentRegion.name.containsIgnoreCase(normalizedKeyword))
                .or(parentRegion.fullName.containsIgnoreCase(normalizedKeyword));
    }

    private boolean hasKeyword(String keyword) {
        return StringUtils.hasText(keyword);
    }

    private BooleanExpression regionCoordinateExists(CourseReqDTO.CourseListReq request) {
        if (resolveSort(request.sort()) != CourseSortType.DISTANCE) {
            return null;
        }

        return region.latitude.isNotNull()
                .and(region.longitude.isNotNull());
    }

    private OrderSpecifier<?>[] orderSpecifiers(
            CourseSortType sort,
            NumberExpression<Long> savedCount,
            NumberExpression<Long> reviewCount,
            NumberExpression<Double> distance
    ) {
        return switch (resolveSort(sort)) {
            case DISTANCE -> new OrderSpecifier<?>[] {
                    distance.asc(),
                    course.id.desc()
            };
            case SAVED -> new OrderSpecifier<?>[] {
                    savedCount.desc(),
                    course.id.desc()
            };
            case REVIEW -> new OrderSpecifier<?>[] {
                    reviewCount.desc(),
                    course.id.desc()
            };
            case LATEST -> new OrderSpecifier<?>[] {
                    course.createdAt.desc(),
                    course.id.desc()
            };
            case RECOMMEND -> new OrderSpecifier<?>[] {
                    recommendOrderExpression().asc(),
                    course.id.desc()
            };
        };
    }

    private BooleanExpression cursorCondition(
            CourseReqDTO.CourseListReq request,
            NumberExpression<Integer> recommendOrder,
            NumberExpression<Long> savedCount,
            NumberExpression<Long> reviewCount,
            NumberExpression<Double> distance
    ) {
        if (request.cursorValue() == null || request.cursorId() == null) {
            return null;
        }

        return switch (resolveSort(request.sort())) {
            case DISTANCE -> distanceCursor(distance, request.cursorValue(), request.cursorId());
            case SAVED -> descendingNumberCursor(savedCount, Long.valueOf(request.cursorValue()), request.cursorId());
            case REVIEW -> descendingNumberCursor(reviewCount, Long.valueOf(request.cursorValue()), request.cursorId());
            case LATEST -> latestCursor(request.cursorValue(), request.cursorId());
            case RECOMMEND -> recommendCursor(
                    recommendOrder,
                    Integer.valueOf(request.cursorValue()),
                    request.cursorId()
            );
        };
    }

    private BooleanExpression distanceCursor(
            NumberExpression<Double> distance,
            String cursorValue,
            Long cursorId
    ) {
        Double cursorDistance = Double.valueOf(cursorValue);

        return distance.gt(cursorDistance)
                .or(distance.eq(cursorDistance)
                        .and(course.id.lt(cursorId)));
    }

    private BooleanExpression descendingNumberCursor(
            NumberExpression<Long> expression,
            Long cursorValue,
            Long cursorId
    ) {
        return expression.lt(cursorValue)
                .or(expression.eq(cursorValue)
                        .and(course.id.lt(cursorId)));
    }

    private BooleanExpression latestCursor(
            String cursorValue,
            Long cursorId
    ) {
        LocalDateTime cursorCreatedAt = LocalDateTime.parse(cursorValue);

        return course.createdAt.lt(cursorCreatedAt)
                .or(course.createdAt.eq(cursorCreatedAt)
                        .and(course.id.lt(cursorId)));
    }

    private BooleanExpression recommendCursor(
            NumberExpression<Integer> recommendOrder,
            Integer cursorValue,
            Long cursorId
    ) {
        return recommendOrder.gt(cursorValue)
                .or(recommendOrder.eq(cursorValue)
                        .and(course.id.lt(cursorId)));
    }

    private NumberExpression<Integer> recommendOrderExpression() {
        return course.recommendOrder.coalesce(RECOMMEND_NULL_ORDER);
    }

    private NumberExpression<Double> distanceExpression(CourseLocation location) {
        if (!location.exists()) {
            return Expressions.numberTemplate(Double.class, "0.0");
        }

        return Expressions.numberTemplate(
                Double.class,
                """
                (6371 * acos(least(1, greatest(-1,
                    cos(radians({0})) * cos(radians({2}))
                    * cos(radians({3}) - radians({1}))
                    + sin(radians({0})) * sin(radians({2}))
                ))))
                """,
                location.latitude(),
                location.longitude(),
                region.latitude,
                region.longitude
        );
    }

    private CourseSortType resolveSort(CourseSortType sort) {
        return sort == null
                ? CourseSortType.RECOMMEND
                : sort;
    }
}
