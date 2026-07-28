package com.yeogido.backend.domain.user.service;

import com.yeogido.backend.domain.user.client.NtsBusinessVerificationClient;
import com.yeogido.backend.domain.user.converter.BusinessVerificationConverter;
import com.yeogido.backend.domain.user.dto.BusinessInfoResDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyReqDTO;
import com.yeogido.backend.domain.user.dto.BusinessVerifyResDTO;
import com.yeogido.backend.domain.user.dto.nts.NtsBusinessVerifyDTO;
import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import com.yeogido.backend.domain.user.exception.BusinessVerificationErrorCode;
import com.yeogido.backend.domain.user.exception.UserErrorCode;
import com.yeogido.backend.domain.user.repository.BusinessInfoRepository;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessVerificationServiceImpl
        implements BusinessVerificationService {

    private static final String VALID_BUSINESS = "01";
    private static final String ACTIVE_BUSINESS = "01";

    private final UserRepository userRepository;
    private final BusinessInfoRepository businessInfoRepository;
    private final NtsBusinessVerificationClient ntsBusinessVerificationClient;

    @Override
    @Transactional
    public BusinessVerifyResDTO verifyBusiness(
            Long userId,
            BusinessVerifyReqDTO request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new GeneralException(UserErrorCode.USER_NOT_FOUND)
                );

        validateDuplicateBusinessNumber(request.businessNumber());

        NtsBusinessVerifyDTO.Result verificationResult =
                ntsBusinessVerificationClient.verify(
                        request.businessNumber(),
                        request.openingDate(),
                        request.representativeName()
                );

        validateAuthenticity(verificationResult);
        validateActiveBusiness(verificationResult);

        BusinessInfo businessInfo =
                BusinessVerificationConverter.toBusinessInfo(
                        user,
                        request,
                        LocalDateTime.now()
                );

        BusinessInfo savedBusinessInfo =
                saveBusinessInfo(businessInfo);

        user.promoteToBusiness();

        return BusinessVerificationConverter.toVerifyResponse(
                user,
                savedBusinessInfo
        );
    }

    @Override
    public List<BusinessInfoResDTO> getApprovedBusinesses(
            Long userId
    ) {
        if (!userRepository.existsById(userId)) {
            throw new GeneralException(
                    UserErrorCode.USER_NOT_FOUND
            );
        }

        return businessInfoRepository
                .findAllByUser_IdAndVerificationStatusOrderByIdDesc(
                        userId,
                        BusinessVerificationStatus.APPROVED
                )
                .stream()
                .map(BusinessVerificationConverter::toBusinessInfoResponse)
                .toList();
    }

    private void validateDuplicateBusinessNumber(
            String businessNumber
    ) {
        if (businessInfoRepository.existsByBusinessNumber(businessNumber)) {
            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_NUMBER_DUPLICATED
            );
        }
    }

    private void validateAuthenticity(
            NtsBusinessVerifyDTO.Result verificationResult
    ) {
        if (!VALID_BUSINESS.equals(verificationResult.valid())) {
            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_VERIFICATION_FAILED
            );
        }
    }

    private void validateActiveBusiness(
            NtsBusinessVerifyDTO.Result verificationResult
    ) {
        NtsBusinessVerifyDTO.Status status = verificationResult.status();

        if (
                status == null
                        || !ACTIVE_BUSINESS.equals(status.businessStatusCode())
        ) {
            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_NOT_ACTIVE
            );
        }
    }

    private BusinessInfo saveBusinessInfo(
            BusinessInfo businessInfo
    ) {
        try {
            return businessInfoRepository.saveAndFlush(businessInfo);
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(
                    BusinessVerificationErrorCode.BUSINESS_NUMBER_DUPLICATED
            );
        }
    }
}