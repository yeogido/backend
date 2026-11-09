package com.yeogido.backend.domain.place.repository;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PlaceRepository extends JpaRepository<Place, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Place p where p.id = :id")
  Optional<Place> findByIdForOverviewEnrichment(@Param("id") Long id);

  @Query("""
      select p.id from Place p
      where p.overviewStatus = com.yeogido.backend.domain.place.enums.PlaceOverviewStatus.PENDING
        and p.overviewAttemptCount < :maxAttempts
        and (p.overview is null or trim(p.overview) = '')
      order by p.id
      """)
  List<Long> findOverviewPendingCandidates(@Param("maxAttempts") int maxAttempts, Pageable pageable);

  @Query("""
      select p.id from Place p
      where ((p.overviewStatus = com.yeogido.backend.domain.place.enums.PlaceOverviewStatus.RETRY_WAIT
          and p.overviewAttemptCount < :maxAttempts and p.overviewNextRetryAt <= :retryBefore)
        or (p.overviewStatus = com.yeogido.backend.domain.place.enums.PlaceOverviewStatus.PROCESSING
          and p.overviewAttemptedAt <= :staleBefore))
        and (p.overview is null or trim(p.overview) = '')
      order by coalesce(p.overviewNextRetryAt, p.overviewAttemptedAt), p.id
      """)
  // 예약 시각이 지난 대기 건 및 횟수 소진 후 상태 정리가 필요한 중단 처리 건 조회
  List<Long> findOverviewRetryCandidates(@Param("retryBefore") LocalDateTime retryBefore,
      @Param("staleBefore") LocalDateTime staleBefore,
      @Param("maxAttempts") int maxAttempts, Pageable pageable);

  Optional<Place> findBySourceAndExternalPlaceId(PlaceSource source, String externalPlaceId);

  List<Place> findBySourceAndExternalPlaceIdIn(PlaceSource source, Collection<String> externalPlaceIds);
}
