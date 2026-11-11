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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

  @Override
  public List<TravelRecord> findUserMapRecords(Long viewerId, Long targetUserId, boolean includeBlurred,
      LocalDate yearStart, LocalDate yearEnd, TravelRecord cursor, int limit) {
    return queryFactory.selectFrom(travelRecord)
        .join(travelRecord.user).fetchJoin()
        .join(travelRecord.region).fetchJoin()
        .where(userMapCollection(viewerId, targetUserId, includeBlurred), endDateBetween(yearStart, yearEnd),
            afterCursor(cursor))
        .orderBy(travelRecord.endDate.desc(), travelRecord.id.desc())
        .limit(limit).fetch();
  }

  @Override
  public Optional<TravelRecord> findUserMapCursor(Long viewerId, Long targetUserId, boolean includeBlurred,
      Long cursorId, LocalDate yearStart, LocalDate yearEnd) {
    return Optional.ofNullable(queryFactory.selectFrom(travelRecord)
        .where(userMapCollection(viewerId, targetUserId, includeBlurred), travelRecord.id.eq(cursorId),
            endDateBetween(yearStart, yearEnd))
        .fetchOne());
  }

  @Override
  public List<Integer> findUserMapYears(Long viewerId, Long targetUserId, boolean includeBlurred) {
    return queryFactory.select(travelRecord.endDate.year()).distinct().from(travelRecord)
        .where(userMapCollection(viewerId, targetUserId, includeBlurred))
        .orderBy(travelRecord.endDate.year().desc()).fetch();
  }

  @Override
  public Set<Long> findVisibleIdsIn(Long viewerId, Long targetUserId, Collection<Long> recordIds) {
    if (recordIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(queryFactory.select(travelRecord.id).from(travelRecord)
        .where(visibleCollection(viewerId, targetUserId, null), travelRecord.id.in(recordIds))
        .fetch());
  }

  /** 목록·커서·연도가 동일한 열람 조건을 사용해 비공개 기록의 존재가 새지 않도록 합니다. */
  private BooleanExpression visibleCollection(Long viewerId, Long targetUserId, TravelRecordVisibility visibility) {
    BooleanExpression collection;
    if (viewerId.equals(targetUserId)) {
      collection = travelRecord.user.id.eq(viewerId)
          .or(travelRecord.visibility.eq(TravelRecordVisibility.FRIENDS).and(currentMember(viewerId)));
    } else {
      // 대상이 만든 PUBLIC 기록과, 생성자가 누구든 조회자와 대상이 둘 다 함께한 FRIENDS 앨범
      collection = travelRecord.user.id.eq(targetUserId).and(travelRecord.visibility.eq(TravelRecordVisibility.PUBLIC))
          .or(travelRecord.visibility.eq(TravelRecordVisibility.FRIENDS)
              .and(joined(viewerId)).and(joined(targetUserId)));
    }
    BooleanExpression result = travelRecord.user.status.eq(UserStatus.ACTIVE).and(collection);
    return visibility == null ? result : result.and(travelRecord.visibility.eq(visibility));
  }

  /** 친구에게는 볼 수 없는 기록도 블러 항목으로 보여 주기 위해 대상이 만든 기록 전부를 더합니다. */
  private BooleanExpression userMapCollection(Long viewerId, Long targetUserId, boolean includeBlurred) {
    BooleanExpression visible = visibleCollection(viewerId, targetUserId, null);
    return includeBlurred
        ? visible.or(travelRecord.user.status.eq(UserStatus.ACTIVE).and(travelRecord.user.id.eq(targetUserId)))
        : visible;
  }

  private BooleanExpression joined(Long userId) {
    return travelRecord.user.id.eq(userId).or(currentMember(userId));
  }

  private BooleanExpression currentMember(Long userId) {
    return JPAExpressions.selectOne().from(travelRecordMember)
        .where(travelRecordMember.travelRecord.eq(travelRecord),
            travelRecordMember.user.id.eq(userId), travelRecordMember.user.status.eq(UserStatus.ACTIVE))
        .exists();
  }

  private BooleanExpression endDateBetween(LocalDate yearStart, LocalDate yearEnd) {
    return yearStart == null ? null : travelRecord.endDate.between(yearStart, yearEnd);
  }

  private BooleanExpression afterCursor(TravelRecord cursor) {
    return cursor == null ? null : travelRecord.endDate.lt(cursor.getEndDate())
        .or(travelRecord.endDate.eq(cursor.getEndDate()).and(travelRecord.id.lt(cursor.getId())));
  }
}
