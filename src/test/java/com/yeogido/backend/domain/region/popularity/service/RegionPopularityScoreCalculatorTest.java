package com.yeogido.backend.domain.region.popularity.service;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionPopularityScoreCalculatorTest {

    private final RegionPopularityScoreCalculator calculator = new RegionPopularityScoreCalculator();

    @Test
    void calculateAppliesRegionPopularityWeights() {
        long score = calculator.calculate(new CoursePopularityActivity(10L, 3L, 2L));

        assertThat(score).isEqualTo(49L);
    }
}
