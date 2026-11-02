package com.yeogido.backend.domain.terms;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.auth.security.JwtAuthenticationFilter;
import com.yeogido.backend.domain.auth.service.JwtTokenProvider;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.repository.UserRepository;
import com.yeogido.backend.global.config.SecurityConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TermsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class TermsControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean TermsService terms;
  @MockitoBean JwtTokenProvider jwt;
  @MockitoBean UserRepository users;

  @Test
  void anonymousCanReadCurrentDocuments() throws Exception {
    when(terms.currentDocuments()).thenReturn(List.of(new TermsDTO.Document(1L, TermsType.SERVICE,
      "1.0", "이용약관", "전문", true, LocalDateTime.of(2026, 11, 2, 0, 0))));
    mvc.perform(get("/api/v1/terms")).andExpect(status().isOk())
      .andExpect(jsonPath("$.result[0].content").value("전문"))
      .andExpect(jsonPath("$.result[0].required").value(true));
  }

  @Test
  void anonymousCannotReadOrWriteConsent() throws Exception {
    mvc.perform(get("/api/v1/users/me/terms")).andExpect(status().isUnauthorized());
    mvc.perform(put("/api/v1/users/me/terms").contentType(MediaType.APPLICATION_JSON)
      .content("{\"agreements\":[]}")).andExpect(status().isUnauthorized());
    verifyNoInteractions(terms);
  }

  @Test
  void consentUsesAuthenticatedUserAndRejectsMissingBodyFields() throws Exception {
    var principal = new UsernamePasswordAuthenticationToken(new AuthUser(7L, UserRole.USER, "session"),
      null, List.of());
    var agreements = List.of(new TermsDTO.Agreement(TermsType.SERVICE, "1.0", true),
      new TermsDTO.Agreement(TermsType.PRIVACY, "1.0", true));
    when(terms.agree(7L, agreements)).thenReturn(new TermsDTO.Status(false, List.of()));
    mvc.perform(put("/api/v1/users/me/terms").with(authentication(principal))
      .contentType(MediaType.APPLICATION_JSON).content("""
        {"userId":99,"agreements":[
          {"type":"SERVICE","version":"1.0","agreed":true},
          {"type":"PRIVACY","version":"1.0","agreed":true}
        ]}
        """))
      .andExpect(status().isOk()).andExpect(jsonPath("$.result.termsAgreementRequired").value(false));
    verify(terms).agree(7L, agreements);

    mvc.perform(put("/api/v1/users/me/terms").with(authentication(principal))
      .contentType(MediaType.APPLICATION_JSON).content("{}"))
      .andExpect(status().isBadRequest());
    verifyNoMoreInteractions(terms);
  }
}
