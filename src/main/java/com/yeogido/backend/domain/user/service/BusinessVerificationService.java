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

    /**
     * 사용자의 승인된 사업자 인증을 모두 해제한다(회원 탈퇴 시 호출).
     * 사업자번호와 장소 연결을 비우고, 그 장소의 활성 홍보글을 내린다.
     */
    void revokeAllByUserId(Long userId);
}