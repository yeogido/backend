package com.yeogido.backend.domain.region.repository;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByName(String name);
    
    Optional<Region> findByFullName(String fullName);

    Optional<Region> findByParentAndName(Region parent, String name);

    List<Region> findByTypeAndParentIsNullOrderByIdAsc(RegionType type);

}
