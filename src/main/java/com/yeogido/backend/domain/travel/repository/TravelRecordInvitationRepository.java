package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordInvitationRepository extends JpaRepository<TravelRecordInvitation, Long>,
        TravelRecordInvitationQueryRepository {

    @Modifying(flushAutomatically = true)
    @Query("""
        delete from TravelRecordInvitation i where i.travelRecord.id = :recordId
          and i.status = com.yeogido.backend.domain.travel.enums.TravelRecordInvitationStatus.PENDING
        """)
    void deletePendingByRecordId(@Param("recordId") Long recordId);

    @Query("select i.travelRecord.id from TravelRecordInvitation i where i.id = :id")
    Optional<Long> findTravelRecordId(@Param("id") Long id);

    @Query("select i.friendship.id from TravelRecordInvitation i where i.id = :id")
    Optional<Long> findFriendshipId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TravelRecordInvitation i where i.id = :id")
    Optional<TravelRecordInvitation> findByIdForUpdate(@Param("id") Long id);

    List<TravelRecordInvitation> findByTravelRecordIdAndInviteeIdIn(Long travelRecordId, List<Long> inviteeIds);
}
