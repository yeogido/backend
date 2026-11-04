package com.yeogido.backend.domain.travel.repository;

import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import java.util.List;
import java.util.Optional;

public interface TravelRecordInvitationQueryRepository {

    List<TravelRecordInvitation> findPendingReceived(Long userId, TravelRecordInvitation cursor, int limit);

    Optional<TravelRecordInvitation> findReceivedCursor(Long userId, Long cursorId);
}
