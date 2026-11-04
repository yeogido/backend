package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordInvitationResDTO;

public interface TravelRecordInvitationService {

    TravelRecordInvitationResDTO.CreateResponse createInvitations(
            Long travelRecordId, Long userId, TravelRecordInvitationReqDTO.CreateRequest request
    );
}
