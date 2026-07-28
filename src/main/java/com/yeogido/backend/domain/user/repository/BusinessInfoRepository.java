package com.yeogido.backend.domain.user.repository;

import com.yeogido.backend.domain.user.entity.BusinessInfo;
import com.yeogido.backend.domain.user.enums.BusinessVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessInfoRepository extends JpaRepository<BusinessInfo, Long> {

    boolean existsByBusinessNumber(String businessNumber);

    List<BusinessInfo>
    findAllByUser_IdAndVerificationStatusOrderByIdDesc(
            Long userId,
            BusinessVerificationStatus verificationStatus
    );
}