package com.yeogido.backend.domain.region.repository;

import com.yeogido.backend.domain.region.entity.Region;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByFullName(String fullName);

    Optional<Region> findByParentAndName(Region parent, String name);

}
