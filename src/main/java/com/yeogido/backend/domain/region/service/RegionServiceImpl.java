package com.yeogido.backend.domain.region.service;

import com.yeogido.backend.domain.region.dto.response.RegionResDTO;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.file.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private final RegionRepository regionRepository;
    private final S3Service s3Service;

    @Override
    public RegionResDTO.RegionListResponse getRegions() {
        List<RegionResDTO.RegionPreview> regions = regionRepository.findByTypeAndParentIsNullOrderByIdAsc(RegionType.REGION)
                .stream()
                .map(this::toRegionPreview)
                .toList();

        return new RegionResDTO.RegionListResponse(regions);
    }

    @Override
    public List<RegionResDTO.PopularRegionRes> getPopularRegions() {
        // TODO: 인기 지역 조회 로직 구현
        return List.of(
                new RegionResDTO.PopularRegionRes(
                        1L,
                        "전주",
                        "전라북도",
                        "https://example.com/regions/jeonju.jpg"
                ),
                new RegionResDTO.PopularRegionRes(
                        2L,
                        "부산",
                        "부산광역시",
                        "https://example.com/regions/busan.jpg"
                ),
                new RegionResDTO.PopularRegionRes(
                        3L,
                        "강릉",
                        "강원특별자치도",
                        "https://example.com/regions/gangneung.jpg"
                ),
                new RegionResDTO.PopularRegionRes(
                        4L,
                        "경주",
                        "경상북도",
                        "https://example.com/regions/gyeongju.jpg"
                ),
                new RegionResDTO.PopularRegionRes(
                        5L,
                        "제주",
                        "제주특별자치도",
                        "https://example.com/regions/jeju.jpg"
                ),
                new RegionResDTO.PopularRegionRes(
                        6L,
                        "여수",
                        "전라남도",
                        "https://example.com/regions/yeosu.jpg"
                )
        );
    }

    private RegionResDTO.RegionPreview toRegionPreview(Region region) {
        return new RegionResDTO.RegionPreview(
                region.getId(),
                region.getName(),
                s3Service.getImageUrl(region.getImageKey())
        );
    }
}
