package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordParticipationLock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordParticipationLockRepository extends JpaRepository<TravelRecordParticipationLock, Long> {
    /** MySQL의 중복 키 UPDATE는 기존 행에도 쓰기 잠금을 걸고 동시 최초 생성은 PK로 직렬화합니다. */
    @Modifying
    @Query(value = """
            INSERT INTO travel_record_participation_lock (user_id) VALUES (:userId)
            ON DUPLICATE KEY UPDATE user_id = user_id
            """, nativeQuery = true)
    void ensureLockRow(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from TravelRecordParticipationLock l where l.userId = :userId")
    Optional<TravelRecordParticipationLock> findForUpdate(@Param("userId") Long userId);
}
