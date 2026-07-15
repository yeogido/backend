package com.yeogido.backend.domain.content.service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.QContent;
import com.yeogido.backend.domain.content.entity.QContentLike;
import com.yeogido.backend.domain.content.repository.ContentLikeRepository;
import com.yeogido.backend.domain.place.entity.QPlace;
import com.yeogido.backend.global.common.dto.NextCursor;
import com.yeogido.backend.global.common.response.ComplexCursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.yeogido.backend.domain.content.enums.ContentSort.DISTANCE;
import static com.yeogido.backend.domain.content.enums.ContentSort.RECOMMEND;


@Service
@RequiredArgsConstructor
public class ContentServiceImpl implements ContentService{

    private final ContentLikeRepository contentLikeRepository;
    private final JPAQueryFactory queryFactory;
    private static final int DEFAULT_PAGE_SIZE = 6;
    private final QContent qContent = QContent.content;
    private final QPlace qPlace = QPlace.place;
    private final QContentLike qContentLike = QContentLike.contentLike;
    private final NumberExpression<Long> likeCountExpression = qContentLike.id.count();

    @Override
    public ComplexCursorResponse<ContentResDTO.ContentInfo> getContents(ContentReqDTO.ContentListReq request){

        BooleanBuilder builder = new BooleanBuilder();

        int size = request.size() == null ? DEFAULT_PAGE_SIZE : request.size();


        LocalDate cursorEndDate = null;
        String cursorValue = request.cursorValue();
        Long cursorId = request.cursorId();

        if (request.regionId() != null && request.regionId() > 0) {
            builder.and(qContent.place.region.id.eq(request.regionId()));
        }

        if (request.category() != null) {
            builder.and(qContent.category.eq(request.category()));
        }

        if (request.keyword() != null && !request.keyword().isBlank()) {
            builder.and(qContent.title.containsIgnoreCase(request.keyword()));
        }

        //TODO : 검색 로직 구현


        List<Content> contents=new ArrayList<>();
        NextCursor<?> nextCursor = null;


        // 정렬 기준
        switch (request.sort()) {

            case LIKE ->{

                Long cursorLikeCount = null;

                if (request.cursorValue() != null) {
                    cursorLikeCount = Long.valueOf(request.cursorValue());
                }

                contents = getLikeContents(
                        builder,
                        size,
                        cursorLikeCount,
                        request.cursorId()
                );

                if (!contents.isEmpty()) {
                    Content last = contents.get(contents.size() - 1);

                    nextCursor = new NextCursor<>(
                            contentLikeRepository.countByContent(last),
                            last.getId()
                    );
                }
            }

            case DEADLINE -> {

                contents = getDeadlineContents(builder, size, cursorId, cursorEndDate);

                Content last = contents.get(contents.size() - 1);

                nextCursor = new NextCursor<>(
                        last.getEndDate(),
                        last.getId()
                );
            }
            case DISTANCE ->{

                nextCursor = getDistanceContents(
                        request,
                        builder,
                        size,
                        contents
                );
            }

            case RECOMMEND -> {
                contents = getRecommendedContents(builder,size);

                //TODO : 추천순 nextCursor 구현
            }
        }

        boolean hasNext = contents.size() > size;

        if (hasNext) {
            contents.remove(contents.size() - 1);
        }



        // TODO : 반환값에 해시태그 추가
        List<ContentResDTO.ContentInfo> result = contents.stream()
                .map(content -> ContentResDTO.ContentInfo.builder()
                        .contentId(content.getId())
                        .placeId(content.getPlace().getId())
                        .title(content.getTitle())
                        .thumbnailImageUrl(content.getThumbnailImage())
                        .regionName(content.getPlace().getRegion().getName())
                        .likeCount(contentLikeRepository.countByContent(content))
                        .startDate(content.getStartDate())
                        .endDate(content.getEndDate())
                        .build())
                .toList();

        return ComplexCursorResponse.of(
                result,
                nextCursor,
                hasNext
        );
    }


    // 저장순(좋아요 많은 순)
    private List<Content> getLikeContents(
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
    private NextCursor<Double> getDistanceContents(
            ContentReqDTO.ContentListReq request,
            BooleanBuilder builder,
            int size,
            List<Content> contents
    ) {

        NumberExpression<Double> avgLatitude = qPlace.latitude.avg();
        NumberExpression<Double> avgLongitude = qPlace.longitude.avg();

        double baseLatitude;
        double baseLongitude;

        //GPS 허용 시 거리순
        if(request.latitude() != null && request.longitude() != null){
            baseLatitude = request.latitude();
            baseLongitude = request.longitude();

        }else {
            if (request.regionId() == null) {
                throw new IllegalArgumentException("GPS를 허용하지 않은 경우 regionId가 필요합니다.");
            }

            //GPS 미허용 시 선택한 지역 중심 기준 거리순
            Tuple center = queryFactory
                    .select(avgLatitude, avgLongitude)
                    .from(qPlace)
                    .where(qPlace.region.id.eq(request.regionId()))
                    .fetchOne();

            if (center == null
                    || center.get(avgLatitude) == null
                    || center.get(avgLongitude) == null) {
                throw new IllegalArgumentException("선택한 지역의 좌표를 찾을 수 없습니다.");
            }

            baseLatitude = center.get(avgLatitude);
            baseLongitude = center.get(avgLongitude);
        }

        NumberExpression<Double> distance =
                createDistanceExpression(baseLatitude, baseLongitude);

        List<Tuple> tuples= queryFactory
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

        Tuple lastTuple = tuples.get(tuples.size() - 1);

        Double lastDistance = lastTuple.get(distance);
        Content lastContent = lastTuple.get(qContent);

        return new NextCursor<>(
                lastDistance,
                lastContent.getId()
        );

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





    @Override
    public ContentResDTO.ContentDetailRes getContentDetail(Long contentId){
        return new ContentResDTO.ContentDetailRes(
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

    }


    @Override
    public ContentResDTO.ContentCreateRes createContent(ContentReqDTO.ContentCreateReq request){
        return new ContentResDTO.ContentCreateRes(null);
    }

    @Override
    public ContentResDTO.ContentUpdateRes updateContent(Long contentId, ContentReqDTO.ContentCreateReq request){
        return new ContentResDTO.ContentUpdateRes(contentId);
    }

    @Override
    public ContentResDTO.ContentLikeRes likeContent(Long contentId){
        return new ContentResDTO.ContentLikeRes(true, 0L);
    }
}
