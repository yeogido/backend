package com.yeogido.backend.domain.region.repository;

import com.yeogido.backend.domain.region.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {
}
