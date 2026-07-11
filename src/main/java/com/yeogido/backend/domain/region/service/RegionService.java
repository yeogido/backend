package com.yeogido.backend.domain.region.service;

import com.yeogido.backend.domain.region.dto.response.RegionResDTO;

import java.util.List;

public interface RegionService {

    List<RegionResDTO.PopularRegionRes> getPopularRegions();
}
