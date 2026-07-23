package com.yeogido.backend.domain.place.service;

import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.place.dto.response.PlaceResponse;
import com.yeogido.backend.domain.place.entity.Place;

import java.util.List;
import java.util.Map;

public interface PlaceService {

    PlaceResponse.PlaceLikeRes createPlaceLike(Long userId, Long placeId);

    PlaceResponse.PlaceLikeRes deletePlaceLike(Long userId, Long placeId);

    Place getOrCreatePlace(CourseReqDTO.CourseItemCreateReq item, Map<String, Place> placeMap);

    Place getOrCreatePlace(ContentReqDTO.PlaceReq request);

    Map<String, Place> getPlaceMap(
            List<CourseReqDTO.CourseItemCreateReq> items
    );

}
