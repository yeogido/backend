package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import com.yeogido.backend.domain.placeenrichment.dto.response.GeminiPlaceResponse;

public interface GeminiPlaceEnrichmentService {

    GeminiPlaceResponse.Result enrich(TourPlaceRequest.Input input);
}
