package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.placeenrichment.entity.PlaceOverviewApiOperation;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.domain.placeenrichment.repository.PlaceOverviewApiOperationRepository;
import com.yeogido.backend.domain.user.service.AdminAuthorizationService;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceOverviewApiAdminService {

    private final AdminAuthorizationService adminAuthorizationService;
    private final PlaceOverviewApiOperationRepository operations;
    private final PlaceOverviewApiGate apiGate;

    // 관리자 권한 확인 후 공통 차단 해제 및 수동 대기 장소의 재시도 예약
    public void release(Long userId, String provider, String operation) {
        adminAuthorizationService.validateAdmin(userId);
        if (!operations.existsById(PlaceOverviewApiOperation.key(provider, operation))) {
            throw new GeneralException(PlaceEnrichmentErrorCode.API_OPERATION_NOT_FOUND);
        }
        apiGate.release(provider, operation);
    }
}
