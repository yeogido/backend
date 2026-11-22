package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.dto.request.TravelRecordInvitationReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordShareResDTO;

public interface TravelRecordShareService {
    TravelRecordShareResDTO.Link createLink(Long recordId, Long userId);
    TravelRecordShareResDTO.View getSharedRecord(String token, Long userId);
    TravelRecordShareResDTO.Respond respond(String token, Long userId, TravelRecordInvitationReqDTO.RespondRequest request);
}
