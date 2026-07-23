package com.yeogido.backend.domain.business.repository;

import com.yeogido.backend.domain.business.entity.BusinessOperatingDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessOperatingDayRepository
        extends JpaRepository<BusinessOperatingDay, Long> {

    void deleteAllByPromotion_Id(Long promotionId);

    List<BusinessOperatingDay> findAllByPromotion_Id(Long promotionId);
}
