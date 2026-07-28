package com.yeogido.backend.domain.region.popularity.dto;

public record RegionPopularityRankingEntry(
        Long regionId,
        long score
) {
}
