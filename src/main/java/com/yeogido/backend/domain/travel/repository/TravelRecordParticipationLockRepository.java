package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordParticipationLock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordParticipationLockRepository extends JpaRepository<TravelRecordParticipationLock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from TravelRecordParticipationLock l where l.userId = :userId")
    Optional<TravelRecordParticipationLock> findForUpdate(@Param("userId") Long userId);
}
