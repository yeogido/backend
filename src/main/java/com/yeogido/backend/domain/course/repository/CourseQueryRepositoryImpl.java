package com.yeogido.backend.domain.course.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.place.entity.QPlace;
import com.yeogido.backend.domain.content.entity.QContent;
import com.yeogido.backend.domain.course.entity.QCourse;
import com.yeogido.backend.domain.course.entity.QCourseItem;
import com.yeogido.backend.domain.course.entity.QCourseLike;
import com.yeogido.backend.domain.course.entity.QCourseReview;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.CourseSortType;
import com.yeogido.backend.domain.region.entity.QRegion;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class CourseQueryRepositoryImpl implements CourseQueryRepository {

    private final JPAQueryFactory queryFactory;

    private final QCourse course = QCourse.course;
    private final QRegion region = QRegion.region;
    private final QCourseLike courseLike = QCourseLike.courseLike;
    private final QCourseReview courseReview = QCourseReview.courseReview;
    private final QCourseItem courseItem = QCourseItem.courseItem;

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
        return findCoursesByCursor(request, location, popularityScores, limit, null);
    }

    @Override
    public List<CourseListRow> findCoursesByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit,
            List<String> searchTerms
    ) {
        return findCoursesByCursor(request, location, popularityScores, limit, searchTerms, null);
    }

    @Override
    public List<CourseListRow> findCoursesByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit,
            List<String> searchTerms,
            Integer totalDays
    ) {
        return findSearchPhaseByCursor(request, location, popularityScores, limit, searchTerms,
                totalDays, null, false);
    }

    @Override
    public List<CourseListRow> findSearchPhaseByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit,
            List<String> searchTerms,
            Integer totalDays,
            String originalKeyword,
            boolean titlePhase
    ) {
        return findSearchPhaseByCursor(request, location, popularityScores, limit, searchTerms,
                totalDays, originalKeyword, titlePhase, null);
    }

    @Override
    public List<CourseListRow> findSearchPhaseByCursor(
            CourseReqDTO.CourseListReq request, CourseLocation location, Map<Long, Long> popularityScores,
            int limit, List<String> searchTerms, Integer totalDays, String originalKeyword,
            boolean titlePhase, SearchMembership exclusion
    ) {
        return findSearchPhase(request, location, popularityScores, limit, searchTerms, totalDays,
                originalKeyword, titlePhase, null, exclusion);
    }

    @Override
    public List<CourseListRow> findSemanticCoursesByCursor(
            CourseReqDTO.CourseListReq request, CourseLocation location, Map<Long, Long> popularityScores,
            int limit, Integer totalDays, String originalKeyword, List<Long> candidateIds
    ) {
        return findSemanticCoursesByCursor(request, location, popularityScores, limit, totalDays,
                originalKeyword, candidateIds, null);
    }

    @Override
    public List<CourseListRow> findSemanticCoursesByCursor(
            CourseReqDTO.CourseListReq request, CourseLocation location, Map<Long, Long> popularityScores,
            int limit, Integer totalDays, String originalKeyword, List<Long> candidateIds, SearchMembership exclusion
    ) {
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        return findSearchPhase(request, location, popularityScores, limit, List.of(), totalDays,
                originalKeyword, false, candidateIds, exclusion);
    }

    @Override
    public SearchMembership findSearchMembership(
            CourseReqDTO.CourseListReq originalRequest, CourseReqDTO.CourseListReq naturalRequest,
            List<String> terms, Integer totalDays, List<Long> semanticCandidateIds,
            CourseLocation location, Map<Long, Long> popularityScores
    ) {
        var ordering = listOrdering(originalRequest.sort(), location, popularityScores);
        String keyword = originalRequest.keyword();
        var membership = searchPhaseCondition(course, region, originalRequest, List.of(), null, keyword, true, null);
        if (semanticCandidateIds == null || !semanticCandidateIds.isEmpty()) {
            membership.or(searchPhaseCondition(course, region, naturalRequest,
                    semanticCandidateIds == null ? terms : List.of(), totalDays, keyword, false, semanticCandidateIds));
        }
        NumberExpression<Integer> titleMatch = hasKeyword(keyword)
                ? new CaseBuilder().when(titleMatchesKeyword(course, keyword)).then(1).otherwise(0)
                : Expressions.asNumber(1);
        var rows = listQuery(Projections.tuple(course.id, titleMatch), ordering)
                .where(membership)
                .orderBy(ordering.specifiers())
                .fetch();
        List<Long> titleIds = new ArrayList<>();
        List<Long> naturalIds = new ArrayList<>();
        for (var row : rows) {
            (Integer.valueOf(1).equals(row.get(titleMatch)) ? titleIds : naturalIds).add(row.get(course.id));
        }
        return new SearchMembership(titleIds, naturalIds);
    }

    @Override
    public List<CourseListRow> findPinnedSearchRows(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        var ordering = listOrdering(CourseSortType.LATEST, new CourseLocation(null, null), Map.of());
        return listQuery(listProjection(ordering), ordering)
                .where(course.deletedAt.isNull(), course.id.in(memberIds))
                .fetch();
    }

    private List<CourseListRow> findSearchPhase(
            CourseReqDTO.CourseListReq request, CourseLocation location, Map<Long, Long> popularityScores,
            int limit, List<String> searchTerms, Integer totalDays, String originalKeyword,
            boolean titlePhase, List<Long> candidateIds, SearchMembership exclusion
    ) {
        var ordering = listOrdering(request.sort(), location, popularityScores);
        JPAQuery<CourseListRow> query = listQuery(listProjection(ordering), ordering)
                .where(searchPhaseCondition(course, region, request, searchTerms, totalDays,
                                originalKeyword, titlePhase, candidateIds),
                        exclusion == null ? null : excludeIds(exclusion.titleIds()),
                        exclusion == null ? null : excludeIds(exclusion.naturalIds()))
                .orderBy(ordering.specifiers())
                .limit(limit);

        BooleanExpression cursorHavingCondition = cursorHavingCondition(request, recommendOrderSortGroupExpression(),
                ordering.savedCount(), ordering.reviewCount(), ordering.distance(), ordering.popularityScore());
        if (cursorHavingCondition != null) {
            if (ordering.aggregateSort()) {
                query.having(cursorHavingCondition);
            } else {
                query.where(cursorHavingCondition);
            }
        }

        return query.fetch();
    }

    private BooleanExpression excludeIds(List<Long> ids) {
        return ids.isEmpty() ? null : course.id.notIn(ids);
    }

    private CourseListOrdering listOrdering(
            CourseSortType requestedSort, CourseLocation location, Map<Long, Long> popularityScores
    ) {
        CourseSortType sort = CourseSortType.resolve(requestedSort);
        NumberExpression<Long> savedCount = sort == CourseSortType.SAVED
                ? courseLike.id.count() : Expressions.numberTemplate(Long.class, "0");
        NumberExpression<Long> reviewCount = sort == CourseSortType.REVIEW
                ? courseReview.id.count() : Expressions.numberTemplate(Long.class, "0");
        NumberExpression<Double> distance = distanceExpression(location);
        NumberExpression<Long> popularityScore = popularityScoreExpression(popularityScores);
        return new CourseListOrdering(sort, savedCount, reviewCount, distance, popularityScore,
                orderSpecifiers(sort, savedCount, reviewCount, distance, popularityScore));
    }

    private Expression<CourseListRow> listProjection(CourseListOrdering ordering) {
        return Projections.constructor(CourseListRow.class, course.id, course.courseType, course.user.id,
                course.thumbnailKey, course.routeImageKey, course.title, region.name, course.durationType,
                course.transportType, course.companionType, course.createdAt, course.recommendOrder,
                ordering.savedCount(), ordering.reviewCount(), ordering.distance(), ordering.popularityScore());
    }

    private <T> JPAQuery<T> listQuery(Expression<T> projection, CourseListOrdering ordering) {
        JPAQuery<T> query = queryFactory.select(projection).from(course).join(course.region, region);
        if (ordering.sort() == CourseSortType.SAVED) {
            query.leftJoin(courseLike).on(courseLike.course.eq(course));
        }
        if (ordering.sort() == CourseSortType.REVIEW) {
            query.leftJoin(courseReview).on(courseReview.course.eq(course));
        }
        if (ordering.aggregateSort()) {
            query.groupBy(course.id, course.courseType, course.user.id, course.thumbnailKey,
                    course.routeImageKey, course.title, region.name, course.durationType, course.transportType,
                    course.companionType, course.createdAt, course.recommendOrder, region.latitude, region.longitude);
        }
        return query;
    }

    private record CourseListOrdering(
            CourseSortType sort,
            NumberExpression<Long> savedCount,
            NumberExpression<Long> reviewCount,
            NumberExpression<Double> distance,
            NumberExpression<Long> popularityScore,
            OrderSpecifier<?>[] specifiers
    ) {
        boolean aggregateSort() {
            return sort == CourseSortType.SAVED || sort == CourseSortType.REVIEW;
        }
    }

    private BooleanBuilder searchPhaseCondition(
            QCourse course, QRegion region, CourseReqDTO.CourseListReq request, List<String> searchTerms,
            Integer totalDays, String originalKeyword, boolean titlePhase, List<Long> candidateIds
    ) {
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(request.courseType() == null ? null : course.courseType.eq(request.courseType()));
        builder.and(course.deletedAt.isNull());
        builder.and(contentItemExists(course, request.contentId()));
        builder.and(regionIdEq(region, request.regionId()));
        builder.and(request.transportType() == null ? null : course.transportType.eq(request.transportType()));
        builder.and(request.durationType() == null ? null : course.durationType.eq(request.durationType()));
        builder.and(totalDays == null ? null : course.totalDays.eq(totalDays));
        builder.and(request.companionType() == null ? null : course.companionType.eq(request.companionType()));
        if (searchTerms == null) {
            builder.and(keywordSearchCondition(course, region, request.keyword()));
        } else {
            for (String term : searchTerms) {
                builder.and(placeSearchCondition(course, region, term));
            }
        }
        if (CourseSortType.resolve(request.sort()) == CourseSortType.DISTANCE) {
            builder.and(region.latitude.isNotNull().and(region.longitude.isNotNull()));
        }
        if (candidateIds != null) {
            builder.and(course.id.in(candidateIds));
        }
        if (hasKeyword(originalKeyword)) {
            BooleanExpression titleMatches = titleMatchesKeyword(course, originalKeyword);
            builder.and(titlePhase ? titleMatches : titleMatches.not());
        }

        return builder;
    }

    private BooleanExpression contentItemExists(QCourse course, Long contentId) {
        if (contentId == null) {
            return null;
        }

        return JPAExpressions
                .selectOne()
                .from(courseItem)
                .where(courseItem.course.eq(course)
                        .and(courseItem.itemType.eq(CourseItemType.CONTENT))
                        .and(courseItem.content.id.eq(contentId)))
                .exists();
    }

    private BooleanExpression regionIdEq(QRegion region, Long regionId) {
        return regionId == null
                ? null
                : region.id.eq(regionId)
                        .or(region.parent.id.eq(regionId));
    }

    private BooleanExpression keywordSearchCondition(QCourse course, QRegion region, String keyword) {
        if (!hasKeyword(keyword)) {
            return null;
        }

        String normalizedKeyword = keyword.trim();

        return course.title.containsIgnoreCase(normalizedKeyword)
                .or(region.name.containsIgnoreCase(normalizedKeyword))
                .or(region.fullName.containsIgnoreCase(normalizedKeyword));
    }

    private BooleanExpression placeSearchCondition(QCourse course, QRegion region, String term) {
        QPlace place = QPlace.place;
        QContent content = QContent.content;
        QPlace contentPlace = new QPlace("searchContentPlace");
        BooleanExpression itemMatches = JPAExpressions.selectOne()
                .from(courseItem)
                .leftJoin(courseItem.place, place)
                .leftJoin(courseItem.content, content)
                .leftJoin(content.place, contentPlace)
                .where(courseItem.course.eq(course).and(
                        place.name.containsIgnoreCase(term)
                                .or(place.overview.containsIgnoreCase(term))
                                .or(content.title.containsIgnoreCase(term))
                                .or(content.description.containsIgnoreCase(term))
                                .or(contentPlace.name.containsIgnoreCase(term))
                                .or(contentPlace.overview.containsIgnoreCase(term))))
                .exists();
        return keywordSearchCondition(course, region, term)
                .or(course.description.containsIgnoreCase(term))
                .or(itemMatches);
    }

    private BooleanExpression titleMatchesKeyword(QCourse course, String keyword) {
        return course.title.containsIgnoreCase(keyword.trim());
    }

    private boolean hasKeyword(String keyword) {
        return StringUtils.hasText(keyword);
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
                    recommendOrderSortGroupExpression().asc(),
                    course.recommendOrder.asc(),
                    course.id.desc()
            };
        };
    }

    private BooleanExpression cursorHavingCondition(
            CourseReqDTO.CourseListReq request,
            NumberExpression<Integer> recommendOrderSortGroup,
            NumberExpression<Long> savedCount,
            NumberExpression<Long> reviewCount,
            NumberExpression<Double> distance,
            NumberExpression<Long> popularityScore
    ) {
        CourseSortType sort = CourseSortType.resolve(request.sort());
        if (request.cursorId() == null
                || (request.cursorValue() == null && sort != CourseSortType.RECOMMEND)) {
            return null;
        }

        return switch (sort) {
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
                    recommendOrderSortGroup,
                    request.cursorValue() == null ? null : Integer.valueOf(request.cursorValue()),
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
            NumberExpression<Integer> recommendOrderSortGroup,
            Integer cursorValue,
            Long cursorId
    ) {
        int cursorSortGroup = recommendOrderSortGroup(cursorValue);

        return recommendOrderSortGroup.gt(cursorSortGroup)
                .or(recommendOrderSortGroup.eq(cursorSortGroup)
                        .and(recommendCursorWithinSameSortGroup(
                                cursorSortGroup,
                                cursorValue,
                                cursorId
                        )));
    }

    private BooleanExpression recommendCursorWithinSameSortGroup(
            int cursorSortGroup,
            Integer cursorValue,
            Long cursorId
    ) {
        if (cursorSortGroup == 2) {
            return course.recommendOrder.isNull()
                    .and(course.id.lt(cursorId));
        }

        if (cursorSortGroup == 1) {
            return course.recommendOrder.eq(0)
                    .and(course.id.lt(cursorId));
        }

        return course.recommendOrder.gt(cursorValue)
                .or(course.recommendOrder.eq(cursorValue)
                        .and(course.id.lt(cursorId)));
    }

    private int recommendOrderSortGroup(Integer recommendOrder) {
        if (recommendOrder == null) {
            return 2;
        }

        return recommendOrder == 0 ? 1 : 0;
    }

    private NumberExpression<Integer> recommendOrderSortGroupExpression() {
        return new CaseBuilder()
                .when(course.recommendOrder.isNull())
                .then(2)
                .when(course.recommendOrder.eq(0))
                .then(1)
                .otherwise(0);
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
