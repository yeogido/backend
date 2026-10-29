package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TravelRecordQueryRepository {

  List<TravelRecord> findVisibleRecords(Long viewerId, Long targetUserId, TravelRecordVisibility visibility,
      LocalDate yearStart, LocalDate yearEnd, TravelRecord cursor, int limit);

  Optional<TravelRecord> findVisibleCursor(Long viewerId, Long targetUserId,
      TravelRecordVisibility visibility, Long cursorId, LocalDate yearStart, LocalDate yearEnd);

  List<Integer> findVisibleYears(Long viewerId, Long targetUserId, TravelRecordVisibility visibility);
}
