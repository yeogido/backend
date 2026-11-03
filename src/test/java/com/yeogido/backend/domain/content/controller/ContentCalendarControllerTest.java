package com.yeogido.backend.domain.content.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.enums.ContentCategory;
import com.yeogido.backend.domain.content.enums.ContentSort;
import com.yeogido.backend.domain.content.service.ContentService;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContentCalendarControllerTest {
  ContentService service;
  MockMvc mvc;
  AuthUser principal;

  @BeforeEach
  void setUp() {
    service = mock(ContentService.class);
    mvc = MockMvcBuilders.standaloneSetup(new ContentController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == AuthUser.class;
          }
          public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
              NativeWebRequest request, WebDataBinderFactory binder) {
            return principal;
          }
        }).build();
  }

  @ParameterizedTest
  @CsvSource({"2026-10-15,2026-10-15", "2026-10-10,2026-10-20", "2026-12-31,2027-01-02",
      "2026-10-01,2026-10-31", "2028-02-01,2028-02-29"})
  void acceptsDayAndRangeQueries(String start, String end) throws Exception {
    LocalDate first = LocalDate.parse(start);
    LocalDate last = LocalDate.parse(end);
    var req = new ContentReqDTO.ContentCalendarReq(first,last);
    when(service.getCalendarContents(req,null))
        .thenReturn(new ContentResDTO.ContentCalendarRes(0,List.of(),first,last));
    mvc.perform(get("/api/v1/contents/calendar").param("startDate",start).param("endDate",end))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.startDate").value(start))
        .andExpect(jsonPath("$.result.endDate").value(end))
        .andExpect(jsonPath("$.result.totalCount").value(0))
        .andExpect(jsonPath("$.result.year").doesNotExist())
        .andExpect(jsonPath("$.result.month").doesNotExist());
    verify(service).getCalendarContents(req,null);
  }

  @ParameterizedTest
  @CsvSource({"2026-10-20,2026-10-10", "2026-02-30,2026-03-01",
      "2026.10.15,2026-10-20", "abc,2026-10-20", "0000-01-01,2026-10-20"})
  void rejectsInvalidDateRange(String start, String end) throws Exception {
    mvc.perform(get("/api/v1/contents/calendar").param("startDate",start).param("endDate",end))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void rejectsMissingDatesAndOldMonthlyParameters() throws Exception {
    mvc.perform(get("/api/v1/contents/calendar"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/contents/calendar").param("startDate","2026-10-10"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/contents/calendar").param("endDate","2026-10-20"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/contents/calendar").param("year","2026").param("month","10"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void passesUserIdAndKeepsActualEventDates() throws Exception {
    principal = new AuthUser(7L, UserRole.values()[0], "test-session");
    LocalDate first = LocalDate.of(2026,10,1);
    LocalDate last = LocalDate.of(2026,10,31);
    var req = new ContentReqDTO.ContentCalendarReq(first,last);
    var content = new ContentResDTO.CalendarContentInfo(1L,"가을 행사",ContentCategory.FESTIVAL,
        "https://example.com/image.jpg","서울",List.of("축제"),2L,true,
        LocalDate.of(2026,9,30),LocalDate.of(2026,11,1));
    when(service.getCalendarContents(req,7L))
        .thenReturn(new ContentResDTO.ContentCalendarRes(1,List.of(content),first,last));
    mvc.perform(get("/api/v1/contents/calendar")
        .param("startDate","2026-10-01").param("endDate","2026-10-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.contents[0].startDate").value("2026.09.30"))
        .andExpect(jsonPath("$.result.contents[0].endDate").value("2026.11.01"))
        .andExpect(jsonPath("$.result.contents[0].isLiked").value(true));
    verify(service).getCalendarContents(req,7L);
  }

  @Test
  void acceptsCategoryAndSort() throws Exception {
    LocalDate date = LocalDate.of(2026,10,15);
    var req = new ContentReqDTO.ContentCalendarReq(date,date,ContentCategory.EXHIBITION,
        ContentSort.DEADLINE,null,null,null);
    when(service.getCalendarContents(req,null))
        .thenReturn(new ContentResDTO.ContentCalendarRes(0,List.of(),date,date));
    mvc.perform(get("/api/v1/contents/calendar").param("startDate","2026-10-15")
        .param("endDate","2026-10-15").param("category","EXHIBITION").param("sort","DEADLINE"))
        .andExpect(status().isOk());
    verify(service).getCalendarContents(req,null);
  }

  @ParameterizedTest
  @CsvSource({"category,INVALID", "sort,INVALID", "regionId,0"})
  void rejectsInvalidFilters(String parameter, String value) throws Exception {
    mvc.perform(get("/api/v1/contents/calendar").param("startDate","2026-10-15")
        .param("endDate","2026-10-15").param(parameter,value))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

}
