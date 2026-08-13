package com.yeogido.backend.domain.user.converter;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.user.dto.BusinessInfoResDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyReqDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyResDTO;
import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;

import java.time.LocalDateTime;

public final class BusinessVerificationConverter {

    private BusinessVerificationConverter() {
    }

    public static BusinessInfo toBusinessInfo(
            User user,
            Place place,
            BusinessVerifyReqDTO request,
            LocalDateTime verifiedAt
    ) {
        return BusinessInfo.builder()
                .user(user)
                .place(place)
                .businessNumber(request.businessNumber())
                .openingDate(request.openingDate())
                .representativeName(request.representativeName())
                .registrationImageKey(request.registrationImageKey())
                .businessName(request.businessName())
                .businessAddress(request.businessAddress())
                .verificationStatus(BusinessVerificationStatus.APPROVED)
                .verifiedAt(verifiedAt)
                .build();
    }

    public static BusinessVerifyResDTO toVerifyResponse(
            User user,
            BusinessInfo businessInfo
    ) {
        return BusinessVerifyResDTO.builder()
                .role(user.getRole())
                .businessInfoId(businessInfo.getId())
                .build();
    }

    public static BusinessInfoResDTO toBusinessInfoResponse(
            BusinessInfo businessInfo
    ) {
        return BusinessInfoResDTO.builder()
                .businessInfoId(businessInfo.getId())
                .businessNumber(businessInfo.getBusinessNumber())
                .businessName(businessInfo.getBusinessName())
                .businessAddress(businessInfo.getBusinessAddress())
                .representativeName(businessInfo.getRepresentativeName())
                .verifiedAt(businessInfo.getVerifiedAt())
                .build();
    }
}