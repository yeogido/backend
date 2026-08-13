package com.yeogido.backend.domain.course.popularity.controller;

import com.yeogido.backend.domain.course.popularity.service.CoursePopularityRankingService;
import com.yeogido.backend.domain.region.popularity.service.RegionPopularityRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Popularity Ranking Test", description = "인기 랭킹 테스트 API")
@Profile("local")
@RestController
@RequestMapping("/api/test/popularity-rankings")
@RequiredArgsConstructor
public class PopularityRankingTestController {

    private final CoursePopularityRankingService coursePopularityRankingService;
    private final RegionPopularityRankingService regionPopularityRankingService;

    @Operation(
            summary = "인기 랭킹 즉시 갱신",
            description = "개발 중 스케줄러를 기다리지 않고 코스/지역 인기 랭킹을 즉시 갱신하는 임시 API입니다."
    )
    @PostMapping("/refresh")
    public ResponseEntity<Void> refreshPopularityRankings() {
        // 개발 환경에서만 사용하는 임시 수동 트리거 API입니다. 운영 반영 전 삭제 대상입니다.
        coursePopularityRankingService.refreshPopularityRankings();
        regionPopularityRankingService.refreshPopularityRankings();

        return ResponseEntity.noContent().build();
    }
}
