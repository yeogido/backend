package com.yeogido.backend.domain.region.popularity.scheduler;

import com.yeogido.backend.domain.region.popularity.service.RegionPopularityRankingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegionPopularityRankingSchedulerTest {

    @Mock
    private RegionPopularityRankingService regionPopularityRankingService;

    @InjectMocks
    private RegionPopularityRankingScheduler regionPopularityRankingScheduler;

    @Test
    void refreshPopularityRankingsDelegatesToService() {
        regionPopularityRankingScheduler.refreshPopularityRankings();

        verify(regionPopularityRankingService).refreshPopularityRankings();
    }
}
