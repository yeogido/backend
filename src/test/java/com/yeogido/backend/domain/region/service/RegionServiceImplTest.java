package com.yeogido.backend.domain.region.service;

import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.region.dto.response.RegionResDTO;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.popularity.repository.RegionPopularityRankingRedisRepository;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionServiceImplTest {

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private RegionPopularityRankingRedisRepository regionPopularityRankingRedisRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private RegionServiceImpl regionService;

    @Test
    void getPopularRegionsReturnsEmptyListWhenRedisRankingIsEmpty() {
        when(regionPopularityRankingRedisRepository.findTopRegionIds(6))
                .thenReturn(List.of());

        List<RegionResDTO.PopularRegionRes> result = regionService.getPopularRegions();

        assertThat(result).isEmpty();
        verify(regionRepository, never()).findByIdIn(List.of());
    }

    @Test
    void getPopularRegionsReturnsRegionsInRedisRankingOrder() {
        Region second = region(2L, "전주시", "전주시", "regions/jeonju.jpg");
        Region first = region(1L, "여수시", "여수시", "regions/yeosu.jpg");

        when(regionPopularityRankingRedisRepository.findTopRegionIds(6))
                .thenReturn(List.of(2L, 1L));
        when(regionRepository.findByIdIn(List.of(2L, 1L)))
                .thenReturn(List.of(first, second));
        when(s3Service.getImageUrl("regions/jeonju.jpg"))
                .thenReturn("https://cdn/jeonju.jpg");
        when(s3Service.getImageUrl("regions/yeosu.jpg"))
                .thenReturn("https://cdn/yeosu.jpg");

        List<RegionResDTO.PopularRegionRes> result = regionService.getPopularRegions();

        assertThat(result).containsExactly(
                new RegionResDTO.PopularRegionRes(2L, "전주시", "전주시", "https://cdn/jeonju.jpg"),
                new RegionResDTO.PopularRegionRes(1L, "여수시", "여수시", "https://cdn/yeosu.jpg")
        );
    }

    private Region region(Long id, String name, String fullName, String imageKey) {
        return Region.builder()
                .id(id)
                .name(name)
                .fullName(fullName)
                .type(RegionType.SUB_REGION)
                .imageKey(imageKey)
                .build();
    }
}
