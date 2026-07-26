package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.user.dto.BusinessInfoResDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyReqDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyResDTO;

import java.util.List;

public interface BusinessVerificationService {

    BusinessVerifyResDTO verifyBusiness(
            Long userId,
            BusinessVerifyReqDTO request
    );

    // 인증 사업장 목록 조회 인터페이스
    List<BusinessInfoResDTO> getApprovedBusinesses(Long userId);
}