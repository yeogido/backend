package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordInvitationRepository extends JpaRepository<TravelRecordInvitation, Long> {

    List<TravelRecordInvitation> findByTravelRecordIdAndInviteeIdIn(Long travelRecordId, List<Long> inviteeIds);
}
