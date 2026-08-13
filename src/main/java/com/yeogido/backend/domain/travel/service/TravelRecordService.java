package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

public interface TravelRecordService {

    CursorResponse<TravelRecordResDTO.TravelRecordSummary> getMyTravelRecords(
            Long userId,
            TravelRecordReqDTO.ListRequest request
    );

    TravelRecordResDTO.YearListResponse getMyTravelRecordYears(Long userId);

    TravelRecordResDTO.DetailResponse getTravelRecord(Long userId, Long travelRecordId);

    TravelRecordResDTO.CreateResponse createTravelRecord(
            Long userId,
            TravelRecordReqDTO.CreateRequest request
    );

    TravelRecordResDTO.UpdateResponse updateTravelRecord(
            Long travelRecordId,
            Long userId,
            TravelRecordReqDTO.UpdateRequest request
    );

    void deleteTravelRecord(Long travelRecordId, Long userId);
}
