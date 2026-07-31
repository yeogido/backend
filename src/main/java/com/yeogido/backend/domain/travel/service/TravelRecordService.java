package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;

public interface TravelRecordService {

    CursorResponse<TravelRecordResDTO.TravelRecordSummary> getMyTravelRecords(
            TravelRecordReqDTO.ListRequest request
    );

    TravelRecordResDTO.YearListResponse getMyTravelRecordYears();

    TravelRecordResDTO.DetailResponse getTravelRecord(Long travelRecordId);

    TravelRecordResDTO.CreateResponse createTravelRecord(
            TravelRecordReqDTO.CreateRequest request
    );

    TravelRecordResDTO.UpdateResponse updateTravelRecord(
            Long travelRecordId,
            Long userId,
            TravelRecordReqDTO.UpdateRequest request
    );

    void deleteTravelRecord(Long travelRecordId, Long userId);
}
