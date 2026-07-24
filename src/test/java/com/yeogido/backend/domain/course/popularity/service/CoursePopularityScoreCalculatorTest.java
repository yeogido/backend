package com.yeogido.backend.domain.course.popularity.service;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityActivity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoursePopularityScoreCalculatorTest {

    private final CoursePopularityScoreCalculator calculator = new CoursePopularityScoreCalculator();

    @Test
    void calculateAppliesViewAndLikeWeights() {
        long score = calculator.calculate(new CoursePopularityActivity(10L, 3L));

        assertThat(score).isEqualTo(58L);
    }

    @Test
    void calculateReturnsZeroWhenNoActivity() {
        assertThat(calculator.calculate(0L, 0L)).isZero();
    }
}
