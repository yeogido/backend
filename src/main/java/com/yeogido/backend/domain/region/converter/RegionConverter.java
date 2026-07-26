package com.yeogido.backend.domain.region.converter;

import com.yeogido.backend.domain.region.dto.response.RegionResDTO;
import com.yeogido.backend.domain.region.entity.Region;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RegionConverter {

    public static RegionResDTO.RegionPreview toRegionPreview(
            Region region,
            String imageUrl
    ) {
        return new RegionResDTO.RegionPreview(
                region.getId(),
                region.getName(),
                imageUrl
        );
    }

    public static RegionResDTO.SubRegionPreview toSubRegionPreview(Region region) {
        return new RegionResDTO.SubRegionPreview(
                region.getId(),
                region.getName()
        );
    }

    public static RegionResDTO.RegionDetailRes toRegionDetailRes(
            Region region,
            String imageUrl
    ) {
        return new RegionResDTO.RegionDetailRes(
                region.getId(),
                region.getName(),
                region.getFullName(),
                imageUrl
        );
    }

    public static RegionResDTO.RegionSearchRes toRegionSearchRes(Region region) {
        return new RegionResDTO.RegionSearchRes(
                region.getId(),
                region.getName(),
                region.getFullName()
        );
    }
}
