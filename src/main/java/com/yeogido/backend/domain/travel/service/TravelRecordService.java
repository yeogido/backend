package com.yeogido.backend.domain.travel.service;

import com.yeogido.backend.domain.travel.dto.request.TravelRecordReqDTO;
import com.yeogido.backend.domain.travel.dto.response.TravelRecordResDTO;
import com.yeogido.backend.global.common.response.CursorResponse;
import java.util.List;

public interface TravelRecordService {

    CursorResponse<TravelRecordResDTO.TravelRecordSummary> getMyTravelRecords(
            Long userId,
            TravelRecordReqDTO.ListRequest request
    );

    CursorResponse<TravelRecordResDTO.MapItem> getMyTravelMap(
            Long userId, TravelRecordReqDTO.MapRequest request
    );

    CursorResponse<TravelRecordResDTO.MapItem> getTravelMap(
            Long viewerId, Long targetUserId, TravelRecordReqDTO.MapRequest request
    );

    /**
     * 다른 사용자 프로필의 여행 지도를 조회합니다. includeBlurred면 열람할 수 없는 대상의 기록도 블러 항목으로 포함하며,
     * year가 null이면 전체 연도를 조회합니다.
     */
    CursorResponse<TravelRecordResDTO.UserMapEntry> getUserTravelMap(
            Long viewerId, Long targetUserId, boolean includeBlurred, Long cursor, Integer size, Integer year
    );

    List<Integer> getUserTravelMapYears(Long viewerId, Long targetUserId, boolean includeBlurred);

    TravelRecordResDTO.YearListResponse getMyTravelRecordYears(
            Long userId, TravelRecordReqDTO.YearListRequest request
    );

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

    TravelRecordResDTO.VisibilityUpdateResponse updateVisibility(
            Long travelRecordId, Long userId, TravelRecordReqDTO.VisibilityUpdateRequest request
    );

    void deleteTravelRecord(Long travelRecordId, Long userId);

    void leaveTravelRecord(Long travelRecordId, Long userId);

    /** 친구 삭제 등 서버 내부 처리에서 생성자가 특정 참여자를 앨범에서 제외합니다. API로 노출하지 않습니다. */
    void excludeMemberByOwner(Long travelRecordId, Long ownerId, Long memberUserId);
}
