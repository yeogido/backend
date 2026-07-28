package com.yeogido.backend.domain.course.popularity.controller;

import com.yeogido.backend.domain.course.popularity.service.CoursePopularityRankingService;
import com.yeogido.backend.domain.region.popularity.service.RegionPopularityRankingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PopularityRankingTestControllerTest {

    @Mock
    private CoursePopularityRankingService coursePopularityRankingService;

    @Mock
    private RegionPopularityRankingService regionPopularityRankingService;

    @InjectMocks
    private PopularityRankingTestController controller;

    @Test
    void refreshPopularityRankingsCallsServiceAndReturnsNoContent() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/test/popularity-rankings/refresh"))
                .andExpect(status().isNoContent());

        verify(coursePopularityRankingService).refreshPopularityRankings();
        verify(regionPopularityRankingService).refreshPopularityRankings();
    }
}