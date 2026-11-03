package com.yeogido.backend.domain.travel.converter;

import com.yeogido.backend.domain.travel.dto.response.TravelRecordInvitationResDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.domain.travel.entity.TravelRecordInvitation;
import com.yeogido.backend.domain.user.entity.User;
import java.util.List;
import java.util.function.Function;

public class TravelRecordInvitationConverter {

    private TravelRecordInvitationConverter() {
    }

    public static TravelRecordInvitationResDTO.RespondResponse toRespondResponse(TravelRecordInvitation invitation) {
        return new TravelRecordInvitationResDTO.RespondResponse(invitation.getId(),
                invitation.getTravelRecord().getId(), invitation.getStatus());
    }

    public static TravelRecordInvitationResDTO.ReceivedInvitation toReceivedInvitation(
            TravelRecordInvitation invitation, Function<String, String> imageUrlResolver
    ) {
        var record = invitation.getTravelRecord();
        return new TravelRecordInvitationResDTO.ReceivedInvitation(invitation.getId(), invitation.getStatus(),
                toUserProfile(invitation.getInviter(), imageUrlResolver),
                toUserProfile(record.getUser(), imageUrlResolver),
                new TravelRecordInvitationResDTO.PendingTravelRecord(record.getId(), record.getTitle(),
                        record.getRegion().getId(), record.getStartDate(), record.getEndDate(), record.getFolderTheme()),
                invitation.getCreatedAt());
    }

    private static TravelRecordResDTO.UserProfile toUserProfile(User user, Function<String, String> imageUrlResolver) {
        return new TravelRecordResDTO.UserProfile(user.getId(), user.getNickname(),
                user.getProfileImage() == null ? null : imageUrlResolver.apply(user.getProfileImage()));
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
