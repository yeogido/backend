package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.placeenrichment.dto.response.TourPlaceResponse;

public interface TourPlaceEnrichmentService {

    TourPlaceResponse.MatchResult enrich(Place place);
}
