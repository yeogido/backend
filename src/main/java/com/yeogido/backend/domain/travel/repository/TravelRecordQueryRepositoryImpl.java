package com.yeogido.backend.domain.travel.repository;

import static com.yeogido.backend.domain.travel.entity.QTravelRecord.travelRecord;
import static com.yeogido.backend.domain.travel.entity.QTravelRecordMember.travelRecordMember;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.user.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class TravelRecordQueryRepositoryImpl implements TravelRecordQueryRepository {

  private final JPAQueryFactory queryFactory;

  public TravelRecordQueryRepositoryImpl(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  @Override
  public List<TravelRecord> findVisibleRecords(Long viewerId, Long targetUserId,
      TravelRecordVisibility visibility, LocalDate yearStart, LocalDate yearEnd, TravelRecord cursor, int limit) {
    return queryFactory.selectFrom(travelRecord)
        .join(travelRecord.user).fetchJoin()
        .join(travelRecord.region).fetchJoin()
        .where(visibleCollection(viewerId, targetUserId, visibility),
            travelRecord.endDate.between(yearStart, yearEnd), afterCursor(cursor))
        .orderBy(travelRecord.endDate.desc(), travelRecord.id.desc())
        .limit(limit).fetch();
  }

  @Override
  public Optional<TravelRecord> findVisibleCursor(Long viewerId, Long targetUserId,
      TravelRecordVisibility visibility, Long cursorId, LocalDate yearStart, LocalDate yearEnd) {
    return Optional.ofNullable(queryFactory.selectFrom(travelRecord)
        .where(visibleCollection(viewerId, targetUserId, visibility), travelRecord.id.eq(cursorId),
            travelRecord.endDate.between(yearStart, yearEnd))
        .fetchOne());
  }

  @Override
  public List<Integer> findVisibleYears(Long viewerId, Long targetUserId, TravelRecordVisibility visibility) {
    return queryFactory.select(travelRecord.endDate.year()).distinct().from(travelRecord)
        .where(visibleCollection(viewerId, targetUserId, visibility))
        .orderBy(travelRecord.endDate.year().desc()).fetch();
  }

  /** 목록·커서·연도가 동일한 열람 조건을 사용해 비공개 기록의 존재가 새지 않도록 합니다. */
  private BooleanExpression visibleCollection(Long viewerId, Long targetUserId, TravelRecordVisibility visibility) {
    BooleanExpression sharedWithViewer = travelRecord.visibility.eq(TravelRecordVisibility.FRIENDS)
        .and(JPAExpressions.selectOne().from(travelRecordMember)
            .where(travelRecordMember.travelRecord.eq(travelRecord),
                travelRecordMember.user.id.eq(viewerId), travelRecordMember.user.status.eq(UserStatus.ACTIVE))
            .exists());
    BooleanExpression collection = viewerId.equals(targetUserId)
        ? travelRecord.user.id.eq(viewerId).or(sharedWithViewer)
        : travelRecord.user.id.eq(targetUserId)
            .and(travelRecord.visibility.eq(TravelRecordVisibility.PUBLIC).or(sharedWithViewer));
    BooleanExpression result = travelRecord.user.status.eq(UserStatus.ACTIVE).and(collection);
    return visibility == null ? result : result.and(travelRecord.visibility.eq(visibility));
  }

  private BooleanExpression afterCursor(TravelRecord cursor) {
    return cursor == null ? null : travelRecord.endDate.lt(cursor.getEndDate())
        .or(travelRecord.endDate.eq(cursor.getEndDate()).and(travelRecord.id.lt(cursor.getId())));
  }
}
