package com.yeogido.backend.domain.course.scheduler;

import com.yeogido.backend.domain.course.service.CoursePopularityRankingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoursePopularityRankingSchedulerTest {

    @Mock
    private CoursePopularityRankingService coursePopularityRankingService;

    @InjectMocks
    private CoursePopularityRankingScheduler coursePopularityRankingScheduler;

    @Test
    void refreshPopularityRankingsDelegatesToService() {
        coursePopularityRankingScheduler.refreshPopularityRankings();

        verify(coursePopularityRankingService).refreshPopularityRankings();
    }
}
