package com.yeogido.backend.domain.region.service;

import com.yeogido.backend.domain.region.dto.response.RegionResDTO;

import java.util.List;

public interface RegionService {

    RegionResDTO.RegionListResponse getRegions();

    RegionResDTO.RegionDetailRes getRegionDetail(Long regionId);

    List<RegionResDTO.RegionSearchRes> searchRegions(String keyword);

    RegionResDTO.SubRegionListResponse getSubRegions(Long regionId);

    List<RegionResDTO.PopularRegionRes> getPopularRegions();
}
