package com.yeogido.backend.domain.region.repository;

import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByName(String name);
    
    Optional<Region> findByFullName(String fullName);

    Optional<Region> findByParentAndName(Region parent, String name);

    Optional<Region> findByIdAndType(Long id, RegionType type);

    @Query("""
            SELECT r
            FROM Region r
            WHERE r.type = :type
              AND r.parent IS NULL
            ORDER BY
              CASE WHEN r.sortOrder IS NULL THEN 1 ELSE 0 END ASC,
              r.sortOrder ASC,
              r.id ASC
            """)
    List<Region> findTopRegionsByDisplayOrder(@Param("type") RegionType type);

    List<Region> findByParentIdAndTypeOrderByIdAsc(Long parentId, RegionType type);

    List<Region> findByIdIn(Collection<Long> ids);

    List<Region> findByNameContainingOrderByIdAsc(String keyword);

}
