package com.yeogido.backend.domain.content.controller;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.content.dto.ContentResDTO;
import com.yeogido.backend.domain.content.service.ContentService;
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
import com.yeogido.backend.global.common.response.CursorResponse;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContentListDateControllerTest {
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

  @Test
  void bindsDatesOnExistingEndpoint() throws Exception {
    var request = new ContentReqDTO.ContentListReq(null,null,null,null,null,null,null,null,null,null,null,
        LocalDate.parse("2026-11-01"),LocalDate.parse("2026-11-30"));
    when(service.getContents(request,null))
        .thenReturn(CursorResponse.<ContentResDTO.ContentInfo>of(List.of(),null,null,false));
    mvc.perform(get("/api/v1/contents").param("startDate","2026-11-01").param("endDate","2026-11-30"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.result.items").isEmpty());
    verify(service).getContents(request,null);
  }

  @ParameterizedTest
  @CsvSource({"2026-11-30,2026-11-01", "2026-02-30,2026-03-01", "2026-11-01,''"})
  void rejectsInvalidDateRange(String start, String end) throws Exception {
    var request = get("/api/v1/contents").param("startDate",start);
    if (!end.isEmpty()) request.param("endDate",end);
    mvc.perform(request).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
