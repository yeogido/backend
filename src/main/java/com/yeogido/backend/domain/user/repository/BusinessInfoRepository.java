package com.yeogido.backend.domain.user.repository;

import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessInfoRepository extends JpaRepository<BusinessInfo, Long> {

    Optional<BusinessInfo> findByBusinessNumber(String businessNumber);

    List<BusinessInfo>
    findAllByUser_IdAndVerificationStatusOrderByIdDesc(
            Long userId,
            BusinessVerificationStatus verificationStatus
    );

    Optional<BusinessInfo> findByIdAndUser_IdAndVerificationStatus(
            Long id,
            Long userId,
            BusinessVerificationStatus verificationStatus
    );
}