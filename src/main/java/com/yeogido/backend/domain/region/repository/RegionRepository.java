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

    Optional<Region> findByIdAndType(Long id, RegionType type);

    List<Region> findByTypeAndParentIsNullOrderByIdAsc(RegionType type);

    List<Region> findByParentIdAndTypeOrderByIdAsc(Long parentId, RegionType type);

    List<Region> findByNameContainingOrderByIdAsc(String keyword);

}
