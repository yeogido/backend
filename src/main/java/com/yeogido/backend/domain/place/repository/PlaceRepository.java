package com.yeogido.backend.domain.place.repository;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findBySourceAndExternalPlaceId(PlaceSource source, String externalPlaceId);

    List<Place> findBySourceAndExternalPlaceIdIn(PlaceSource source, Collection<String> externalPlaceIds);
}
