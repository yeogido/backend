package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, Long> {

    @Query("""
            select distinct year(tr.endDate)
            from TravelRecord tr
            where tr.user.id = :userId
            order by year(tr.endDate) desc
            """)
    List<Integer> findTravelYearsByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = "region")
    @Query("""
            select tr
            from TravelRecord tr
            where tr.user.id = :userId
              and tr.endDate between :yearStart and :yearEnd
            order by tr.endDate desc, tr.id desc
            """)
    List<TravelRecord> findByUserIdAndTravelYear(
            @Param("userId") Long userId,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "region")
    @Query("""
            select tr
            from TravelRecord tr
            where tr.user.id = :userId
              and tr.endDate between :yearStart and :yearEnd
              and (
                    tr.endDate < :cursorEndDate
                    or (tr.endDate = :cursorEndDate and tr.id < :cursorId)
              )
            order by tr.endDate desc, tr.id desc
            """)
    List<TravelRecord> findByUserIdAndTravelYearAfterCursor(
            @Param("userId") Long userId,
            @Param("yearStart") LocalDate yearStart,
            @Param("yearEnd") LocalDate yearEnd,
            @Param("cursorEndDate") LocalDate cursorEndDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
