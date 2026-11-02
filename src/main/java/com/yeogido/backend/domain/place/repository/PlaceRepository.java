package com.yeogido.backend.domain.place.repository;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PlaceRepository extends JpaRepository<Place, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Place p where p.id = :id")
  Optional<Place> findByIdForOverviewEnrichment(@Param("id") Long id);

  Optional<Place> findBySourceAndExternalPlaceId(PlaceSource source, String externalPlaceId);

  List<Place> findBySourceAndExternalPlaceIdIn(PlaceSource source, Collection<String> externalPlaceIds);
}
