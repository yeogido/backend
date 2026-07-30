package com.yeogido.backend.domain.region.service;

import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.region.converter.RegionConverter;
import com.yeogido.backend.domain.region.dto.response.RegionResDTO;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.exception.RegionErrorCode;
import com.yeogido.backend.domain.region.popularity.repository.RegionPopularityRankingRedisRepository;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private static final int POPULAR_REGION_SIZE = 6;

    private final RegionRepository regionRepository;
    private final RegionPopularityRankingRedisRepository regionPopularityRankingRedisRepository;
    private final S3Service s3Service;

    @Override
    public RegionResDTO.RegionListResponse getRegions() {
        List<RegionResDTO.RegionPreview> regions = regionRepository.findTopRegionsByDisplayOrder(RegionType.REGION)
                .stream()
                .map(region -> RegionConverter.toRegionPreview(
                        region,
                        s3Service.getImageUrl(region.getImageKey())
                ))
                .toList();

        return new RegionResDTO.RegionListResponse(regions);
    }

    @Override
    public RegionResDTO.RegionDetailRes getRegionDetail(Long regionId) {
        Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));

        return RegionConverter.toRegionDetailRes(
                region,
                s3Service.getImageUrl(region.getImageKey())
        );
    }

    @Override
    public List<RegionResDTO.RegionSearchRes> searchRegions(String keyword) {
        return regionRepository.findByNameContainingOrderByIdAsc(keyword)
                .stream()
                .map(RegionConverter::toRegionSearchRes)
                .toList();
    }

    @Override
    public RegionResDTO.SubRegionListResponse getSubRegions(Long regionId) {
        regionRepository.findByIdAndType(regionId, RegionType.REGION)
                .orElseThrow(() -> new GeneralException(RegionErrorCode.REGION_NOT_FOUND));

        List<RegionResDTO.SubRegionPreview> subRegions = regionRepository.findByParentIdAndTypeOrderByNameAsc(
                        regionId,
                        RegionType.SUB_REGION
                )
                .stream()
                .map(RegionConverter::toSubRegionPreview)
                .toList();

        return new RegionResDTO.SubRegionListResponse(subRegions);
    }

    @Override
    public List<RegionResDTO.PopularRegionRes> getPopularRegions() {
        List<Long> regionIds = regionPopularityRankingRedisRepository.findTopRegionIds(POPULAR_REGION_SIZE);

        if (regionIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Region> regionMap = regionRepository.findByIdIn(regionIds)
                .stream()
                .collect(Collectors.toMap(Region::getId, Function.identity()));

        return regionIds.stream()
                .map(regionMap::get)
                .filter(region -> region != null)
                .map(region -> RegionConverter.toPopularRegionRes(
                        region,
                        s3Service.getImageUrl(region.getImageKey())
                ))
                .toList();
    }

}
