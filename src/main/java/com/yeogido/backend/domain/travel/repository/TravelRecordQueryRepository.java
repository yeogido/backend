package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TravelRecordQueryRepository {

  List<TravelRecord> findVisibleRecords(Long viewerId, Long targetUserId, TravelRecordVisibility visibility,
      LocalDate yearStart, LocalDate yearEnd, TravelRecord cursor, int limit);

  Optional<TravelRecord> findVisibleCursor(Long viewerId, Long targetUserId,
      TravelRecordVisibility visibility, Long cursorId, LocalDate yearStart, LocalDate yearEnd);

  List<Integer> findVisibleYears(Long viewerId, Long targetUserId, TravelRecordVisibility visibility);

  /**
   * 다른 사용자 프로필의 여행 지도 범위를 조회합니다. includeBlurred면 대상이 만든 기록 전부를 더하며,
   * 연도 범위가 null이면 전체 연도를 조회합니다.
   */
  List<TravelRecord> findUserMapRecords(Long viewerId, Long targetUserId, boolean includeBlurred,
      LocalDate yearStart, LocalDate yearEnd, TravelRecord cursor, int limit);

  Optional<TravelRecord> findUserMapCursor(Long viewerId, Long targetUserId, boolean includeBlurred,
      Long cursorId, LocalDate yearStart, LocalDate yearEnd);

  List<Integer> findUserMapYears(Long viewerId, Long targetUserId, boolean includeBlurred);

  /** 주어진 기록 중 조회자가 열람할 수 있는 기록 ID만 반환합니다. */
  Set<Long> findVisibleIdsIn(Long viewerId, Long targetUserId, Collection<Long> recordIds);
}
