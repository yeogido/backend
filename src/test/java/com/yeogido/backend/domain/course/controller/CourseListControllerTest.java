package com.yeogido.backend.domain.course.controller;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;
import com.yeogido.backend.domain.course.dto.response.CourseResDTO.CourseListRes;
import com.yeogido.backend.domain.course.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseListControllerTest {
    private static final String COURSES_PATH = "/api/v1/courses";
    private static final UUID SEARCH_ID = UUID.fromString("57c50c31-2966-4ea3-98a8-de63b979c2f3");

    private final CourseService service = mock(CourseService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CourseController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void returnsSearchIdAndExtractedKeywordsForInitialSearch() throws Exception {
        var request = new CourseListReq(null, "부산에서 비 오는 날", null, null, null, null, null,
                null, null, null, null, null, 20);
        when(service.getCourses(request, null)).thenReturn(
                new CourseListRes(List.of(), null, null, false, List.of("부산", "비 오는 날"), SEARCH_ID));

        mvc.perform(get(COURSES_PATH).queryParam("keyword", "부산에서 비 오는 날"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.searchId").value(SEARCH_ID.toString()))
                .andExpect(jsonPath("$.result.extractedKeywords[1]").value("비 오는 날"));
    }

    @Test
    void bindsRepeatedKeywordsAsSeparateValues() throws Exception {
        assertKeywordBinding("부산", "비 오는 날");
    }

    @Test
    void keepsACommaWithinOneSelectedKeyword() throws Exception {
        assertKeywordBinding("박물관, 미술관");
    }

    private void assertKeywordBinding(String... keywords) throws Exception {
        var request = new CourseListReq(null, null, null, null, null, null, null,
                null, null, null, null, null, 20, SEARCH_ID, List.of(keywords));
        when(service.getCourses(request, null)).thenReturn(
                new CourseListRes(List.of(), null, null, false, List.of()));

        mvc.perform(get(COURSES_PATH).queryParam("searchId", SEARCH_ID.toString())
                        .queryParam("keywords", keywords))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.searchId").doesNotExist());
        verify(service).getCourses(request, null);
    }
}
