package com.yeogido.backend.domain.course.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.CaseBuilder;
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
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class CourseQueryRepositoryImpl implements CourseQueryRepository {

    private static final int RECOMMEND_NULL_ORDER = 1_000_000_000;

    private final JPAQueryFactory queryFactory;

    private final QCourse course = QCourse.course;
    private final QRegion region = QRegion.region;
    private final QCourseLike courseLike = QCourseLike.courseLike;
    private final QCourseReview courseReview = QCourseReview.courseReview;

    public CourseQueryRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<CourseListRow> findCoursesByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit
    ) {
        CourseSortType sort = CourseSortType.resolve(request.sort());
        boolean savedSort = sort == CourseSortType.SAVED;
        boolean reviewSort = sort == CourseSortType.REVIEW;
        boolean aggregateSort = savedSort || reviewSort;

        NumberExpression<Long> savedCount = savedSort
                ? courseLike.id.count()
                : Expressions.numberTemplate(Long.class, "0");
        NumberExpression<Long> reviewCount = reviewSort
                ? courseReview.id.count()
                : Expressions.numberTemplate(Long.class, "0");
        NumberExpression<Double> distance = distanceExpression(location);
        NumberExpression<Integer> recommendOrder = recommendOrderExpression();
        NumberExpression<Long> popularityScore = popularityScoreExpression(popularityScores);

        JPAQuery<CourseListRow> query = queryFactory
                .select(Projections.constructor(
                        CourseListRow.class,
                        course.id,
                        course.thumbnailKey,
                        course.routeImageKey,
                        course.title,
                        region.name,
                        course.durationType,
                        course.transportType,
                        course.companionType,
                        course.createdAt,
                        recommendOrder,
                        savedCount,
                        reviewCount,
                        distance,
                        popularityScore
                ))
                .from(course)
                .join(course.region, region);

        if (savedSort) {
            query.leftJoin(courseLike).on(courseLike.course.eq(course));
        }

        if (reviewSort) {
            query.leftJoin(courseReview).on(courseReview.course.eq(course));
        }

        if (aggregateSort) {
            query.groupBy(
                    course.id,
                    course.thumbnailKey,
                    course.routeImageKey,
                    course.title,
                    region.name,
                    course.durationType,
                    course.transportType,
                    course.companionType,
                    course.createdAt,
                    course.recommendOrder,
                    region.latitude,
                    region.longitude
            );
        }

        query.where(courseListCondition(request))
                .orderBy(orderSpecifiers(sort, savedCount, reviewCount, distance, popularityScore))
                .limit(limit);

        BooleanExpression cursorHavingCondition = cursorHavingCondition(
                request,
                recommendOrder,
                savedCount,
                reviewCount,
                distance,
                popularityScore
        );

        if (cursorHavingCondition != null) {
            if (aggregateSort) {
                query.having(cursorHavingCondition);
            } else {
                query.where(cursorHavingCondition);
            }
        }

        return query.fetch();
    }

    private BooleanBuilder courseListCondition(CourseReqDTO.CourseListReq request) {
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(course.courseType.eq(request.courseType()));
        builder.and(course.deletedAt.isNull());
        builder.and(regionIdEq(request.regionId()));
        builder.and(transportTypeEq(request));
        builder.and(durationTypeEq(request));
        builder.and(companionTypeEq(request));
        builder.and(keywordSearchCondition(request.keyword()));
        builder.and(distanceSortRegionCoordinateExists(request));

        return builder;
    }

    private BooleanExpression regionIdEq(Long regionId) {
        return regionId == null
                ? null
                : region.id.eq(regionId)
                        .or(region.parent.id.eq(regionId));
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

    private BooleanExpression keywordSearchCondition(String keyword) {
        if (!hasKeyword(keyword)) {
            return null;
        }

        String normalizedKeyword = keyword.trim();

        return course.title.containsIgnoreCase(normalizedKeyword)
                .or(region.name.containsIgnoreCase(normalizedKeyword))
                .or(region.fullName.containsIgnoreCase(normalizedKeyword));
    }

    private boolean hasKeyword(String keyword) {
        return StringUtils.hasText(keyword);
    }

    private BooleanExpression distanceSortRegionCoordinateExists(CourseReqDTO.CourseListReq request) {
        if (CourseSortType.resolve(request.sort()) != CourseSortType.DISTANCE) {
            return null;
        }

        return region.latitude.isNotNull()
                .and(region.longitude.isNotNull());
    }

    private OrderSpecifier<?>[] orderSpecifiers(
            CourseSortType sort,
            NumberExpression<Long> savedCount,
            NumberExpression<Long> reviewCount,
            NumberExpression<Double> distance,
            NumberExpression<Long> popularityScore
    ) {
        return switch (CourseSortType.resolve(sort)) {
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
            case POPULAR -> new OrderSpecifier<?>[] {
                    popularityScore.desc(),
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

    private BooleanExpression cursorHavingCondition(
            CourseReqDTO.CourseListReq request,
            NumberExpression<Integer> recommendOrder,
            NumberExpression<Long> savedCount,
            NumberExpression<Long> reviewCount,
            NumberExpression<Double> distance,
            NumberExpression<Long> popularityScore
    ) {
        if (request.cursorValue() == null || request.cursorId() == null) {
            return null;
        }

        return switch (CourseSortType.resolve(request.sort())) {
            case DISTANCE -> distanceCursor(distance, request.cursorValue(), request.cursorId());
            case SAVED -> descendingNumberCursor(savedCount, Long.valueOf(request.cursorValue()), request.cursorId());
            case REVIEW -> descendingNumberCursor(reviewCount, Long.valueOf(request.cursorValue()), request.cursorId());
            case POPULAR -> descendingNumberCursor(
                    popularityScore,
                    Long.valueOf(request.cursorValue()),
                    request.cursorId()
            );
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

    private NumberExpression<Long> popularityScoreExpression(Map<Long, Long> popularityScores) {
        if (popularityScores == null || popularityScores.isEmpty()) {
            return Expressions.numberTemplate(Long.class, "0");
        }

        CaseBuilder.Cases<Long, NumberExpression<Long>> cases = null;
        for (Map.Entry<Long, Long> entry : popularityScores.entrySet()) {
            if (cases == null) {
                cases = new CaseBuilder()
                        .when(course.id.eq(entry.getKey()))
                        .then(entry.getValue());
            } else {
                cases = cases
                        .when(course.id.eq(entry.getKey()))
                        .then(entry.getValue());
            }
        }

        return cases == null
                ? Expressions.numberTemplate(Long.class, "0")
                : cases.otherwise(0L);
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

}
