package com.yeogido.backend.domain.content.service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.entity.QContent;
import com.yeogido.backend.domain.content.entity.QContentLike;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.repository.ContentLikeRepository;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.place.entity.QPlace;
import com.yeogido.backend.global.common.response.CursorResponse;
import com.yeogido.backend.global.common.response.StringCursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;


@Service
@RequiredArgsConstructor
public class ContentServiceImpl implements ContentService{

    private final ContentLikeRepository contentLikeRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public StringCursorResponse<ContentResDTO.ContentInfo> getContents(ContentReqDTO.ContentListReq request){

        QContent qContent = QContent.content;
        QContentLike qContentLike = QContentLike.contentLike;
        QPlace qPlace =QPlace.place;
        NumberExpression<Long> likeCount = qContentLike.id.count();

        BooleanBuilder builder = new BooleanBuilder();

        int size = request.size() == null ? 6 : request.size();
        Long cursorLikeCount = null;
        Long cursorId = null;
        LocalDate cursorEndDate = null;
        Double cursorDistance = null;

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

        //종료 임박순 : id 파싱
        if ("종료 임박순".equals(request.sort()) && request.cursor() != null && !request.cursor().isBlank()) {

            String[] cursor = request.cursor().split(":");

            cursorEndDate = LocalDate.parse(cursor[0]);
            cursorId = Long.parseLong(cursor[1]);

            builder.and(
                    qContent.endDate.gt(cursorEndDate)
                            .or(
                                    qContent.endDate.eq(cursorEndDate)
                                            .and(qContent.id.gt(cursorId))
                            )
            );
        }


        // 저장순 : id 파싱
        if ("저장순".equals(request.sort()) && request.cursor() != null && !request.cursor().isBlank()) {

            String[] cursor = request.cursor().split(":");

            cursorLikeCount = Long.parseLong(cursor[0]);
            cursorId = Long.parseLong(cursor[1]);
        }



        Double baseLatitude=null;
        Double baseLongitude=null;

        List<Content> contents;
        List<Tuple> tuples = null;
        NumberExpression<Double> distance = null;

        // 정렬 기준
        if ("저장순".equals(request.sort())) {

            var query = queryFactory
                    .selectFrom(qContent)
                    .leftJoin(qContentLike)
                    .on(qContentLike.content.eq(qContent))
                    .where(builder)
                    .groupBy(qContent.id);

            if (cursorLikeCount != null && cursorId != null) {

                query.having(
                        likeCount.lt(cursorLikeCount)
                                .or(likeCount.eq(cursorLikeCount)
                                                .and(qContent.id.gt(cursorId))
                                )
                );
            }

            contents = query
                    .orderBy(likeCount.desc(), qContent.id.asc())
                    .limit(size + 1)
                    .fetch();


        } else if ("종료 임박순".equals(request.sort())) {

            builder.and(qContent.endDate.goe(LocalDate.now()));

            contents = queryFactory
                    .selectFrom(qContent)
                    .where(builder)
                    .orderBy(qContent.endDate.asc(), qContent.id.asc())
                    .limit(size+1)
                    .fetch();

        } else if ("거리순".equals(request.sort())) {

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
                        .select(
                                qPlace.latitude.avg(),
                                qPlace.longitude.avg()
                        )
                        .from(qPlace)
                        .where(qPlace.region.id.eq(request.regionId()))
                        .fetchOne();

                if (center == null
                        || center.get(qPlace.latitude.avg()) == null
                        || center.get(qPlace.longitude.avg()) == null) {
                    throw new IllegalArgumentException("선택한 지역의 좌표를 찾을 수 없습니다.");
                }

                baseLatitude = center.get(qPlace.latitude.avg()).doubleValue();
                baseLongitude = center.get(qPlace.longitude.avg()).doubleValue();
            }

            //거리 계산식
            distance =
                    Expressions.numberTemplate(
                            Double.class,
                            "POWER({0} - {1}, 2) + POWER({2} - {3}, 2)",
                            qContent.place.latitude,
                            baseLatitude,
                            qContent.place.longitude,
                            baseLongitude
                    );

            if (request.cursor() != null && !request.cursor().isBlank()) {

                String[] cursor = request.cursor().split(":");

                cursorDistance = Double.parseDouble(cursor[0]);
                cursorId = Long.parseLong(cursor[1]);

                builder.and(
                        distance.gt(cursorDistance)
                                .or(
                                        distance.eq(cursorDistance)
                                                .and(qContent.id.gt(cursorId))
                                )
                );
            }

            tuples = queryFactory
                    .select(qContent, distance)
                    .from(qContent)
                    .where(builder)
                    .orderBy(distance.asc(), qContent.id.asc())
                    .limit(size + 1)
                    .fetch();

            contents = new ArrayList<>(
                    tuples.stream()
                            .map(tuple -> tuple.get(qContent))
                            .toList()
            );

        } else {

            // TODO : 추천순 조회 로직 구현

            contents = queryFactory
                    .selectFrom(qContent)
                    .where(builder)
                    .orderBy(qContent.id.asc())
                    .limit(size + 1)
                    .fetch();

        }

        boolean hasNext = contents.size() > size;

        if (hasNext) {
            contents.remove(contents.size() - 1);

            if (tuples != null) {
                tuples.remove(tuples.size() - 1);
            }
        }


        // nextCursor 생성
        String nextCursor = null;

        if (!contents.isEmpty()) {
            Content last = contents.get(contents.size() - 1);

            if ("저장순".equals(request.sort())) {

                long likeCountValue = contentLikeRepository.countByContent(last);
                nextCursor = likeCountValue + ":" + last.getId();

            } else if ("종료 임박순".equals(request.sort())) {

                nextCursor = last.getEndDate() + ":" + last.getId();

            } else if ("거리순".equals(request.sort())) {

                Tuple lastTuple = tuples.get(tuples.size() - 1);

                last = lastTuple.get(qContent);
                Double lastDistance = lastTuple.get(distance);

                nextCursor = lastDistance + ":" + last.getId();

            } else {

                // TODO:추천순 커서 구현
                nextCursor = last.getId().toString();
            }
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

        return StringCursorResponse.of(
                result,
                nextCursor,
                hasNext
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
