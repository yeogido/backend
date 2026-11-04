package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordInvitationResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

public interface TravelRecordInvitationService {

    CursorResponse<TravelRecordInvitationResDTO.ReceivedInvitation> getReceivedInvitations(
            Long userId, TravelRecordInvitationReqDTO.ListRequest request
    );

    TravelRecordInvitationResDTO.CreateResponse createInvitations(
            Long travelRecordId, Long userId, TravelRecordInvitationReqDTO.CreateRequest request
    );
}
