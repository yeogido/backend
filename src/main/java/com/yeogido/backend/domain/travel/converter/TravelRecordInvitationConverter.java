package com.yeogido.backend.domain.travel.converter;

import com.yeogido.backend.domain.travel.dto.response.TravelRecordInvitationResDTO;
import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import java.util.List;

public class TravelRecordInvitationConverter {

    private TravelRecordInvitationConverter() {
    }

    public static TravelRecordInvitationResDTO.CreateResponse toCreateResponse(
            Long travelRecordId, List<TravelRecordInvitation> invitations
    ) {
        return new TravelRecordInvitationResDTO.CreateResponse(travelRecordId,
                invitations.stream().map(invitation -> new TravelRecordInvitationResDTO.InvitationItem(
                        invitation.getId(), invitation.getInvitee().getId(),
                        invitation.getStatus(), invitation.getCreatedAt())).toList());
    }
}
