package com.yeogido.backend.domain.content.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.QContent;
import com.yeogido.backend.domain.content.entity.QContentLike;
import com.yeogido.backend.domain.content.enums.ContentPublicationStatus;
import com.yeogido.backend.domain.content.enums.ContentSort;
import com.yeogido.backend.domain.place.entity.QPlace;
import com.yeogido.backend.domain.region.entity.QRegion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ContentCalendarQueryRepository {
    private final JPAQueryFactory queryFactory;

    public List<Content> findCalendarContents(
            ContentReqDTO.ContentCalendarReq request, List<Long> regionIds,
            Double latitude, Double longitude
    ) {
        QContent content = QContent.content;
        QPlace place = QPlace.place;
        QRegion region = QRegion.region;
        QContentLike like = QContentLike.contentLike;

        BooleanBuilder filter = new BooleanBuilder()
                .and(content.publicationStatus.eq(ContentPublicationStatus.PUBLISHED))
                .and(content.startDate.loe(request.endDate()))
                .and(content.endDate.goe(request.startDate()));
        if (request.category() != null) {
            filter.and(content.category.eq(request.category()));
        }
        if (!regionIds.isEmpty()) {
            filter.and(place.region.id.in(regionIds));
        }

        var query = queryFactory.selectFrom(content)
                .join(content.place, place).fetchJoin()
                .join(place.region, region).fetchJoin()
                .where(filter);
        ContentSort sort = request.sort() == null ? ContentSort.RECOMMEND : request.sort();
        switch (sort) {
            case RECOMMEND -> {
                NumberExpression<Integer> unrecommended = new CaseBuilder()
                        .when(content.recommendPriority.eq(0)).then(1).otherwise(0);
                query.orderBy(unrecommended.asc(), content.recommendPriority.asc(), content.id.asc());
            }
            case LIKE -> query.leftJoin(like).on(like.content.eq(content))
                    .groupBy(content.id, place.id, region.id)
                    .orderBy(like.id.count().desc(), content.id.asc());
            case DEADLINE -> query.orderBy(content.endDate.asc(), content.id.asc());
            case DISTANCE -> {
                // 기존 행사 목록과 같은 좌표 차이 제곱합으로 정렬합니다.
                NumberExpression<Double> distance = Expressions.numberTemplate(Double.class,
                        "POWER({0} - {1}, 2) + POWER({2} - {3}, 2)",
                        place.latitude, latitude, place.longitude, longitude);
                query.orderBy(distance.asc(), content.id.asc());
            }
        }
        return query.fetch();
    }
}
