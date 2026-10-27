package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.placeenrichment.dto.response.TourPlaceResDTO;

public interface TourPlaceEnrichmentService {

    TourPlaceResDTO.MatchResult enrich(Place place);
}
