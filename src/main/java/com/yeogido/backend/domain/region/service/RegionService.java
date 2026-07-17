package com.yeogido.backend.domain.region.service;

import com.yeogido.backend.domain.region.dto.response.RegionResDTO;

import java.util.List;

public interface RegionService {

    RegionResDTO.RegionListResponse getRegions();

    List<RegionResDTO.PopularRegionRes> getPopularRegions();
}
