package com.yeogido.backend.domain.business.repository;

import com.yeogido.backend.domain.business.entity.BusinessOperatingDay;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessOperatingDayRepository
        extends JpaRepository<BusinessOperatingDay, Long> {

    void deleteAllByPromotion_Id(Long promotionId);

}
