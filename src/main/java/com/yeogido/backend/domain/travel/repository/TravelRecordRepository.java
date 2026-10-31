package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, Long>, TravelRecordQueryRepository {

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from TravelRecord tr
            where tr.user.id = :userId
            """)
    int deleteAllByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "region"})
    @Query("""
            select tr from TravelRecord tr
            where tr.id = :id and tr.user.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
            """)
    Optional<TravelRecord> findActiveDetailById(@Param("id") Long id);
}
