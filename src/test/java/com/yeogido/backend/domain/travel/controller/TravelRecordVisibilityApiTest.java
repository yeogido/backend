package com.yeogido.backend.domain.travel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yeogido.backend.domain.auth.security.AuthUser;
import com.yeogido.backend.domain.file.service.FileService;
import com.yeogido.backend.domain.file.service.S3Service;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.travel.entity.TravelRecord;
import com.yeogido.backend.domain.travel.enums.TravelRecordVisibility;
import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.Gender;
import com.yeogido.backend.domain.user.enums.UserRole;
import com.yeogido.backend.domain.user.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:travel-visibility-api;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TravelRecordVisibilityApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private EntityManager entityManager;
  @MockitoBean private FileService fileService;
  @MockitoBean private S3Service s3Service;
  private Region region;
  private User viewer;
  private User owner;
  private TravelRecord publicRecord;

  @BeforeEach
  void setUp() {
    region = entityManager.merge(Region.builder().name("부산").type(RegionType.REGION).build());
    viewer = user("조회자");
    owner = user("생성자");
    publicRecord = entityManager.merge(TravelRecord.builder().user(owner).region(region).title("공개 여행")
        .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
        .visibility(TravelRecordVisibility.PUBLIC).coverImageKey("travel-records/public.jpg").build());
    entityManager.flush();
    when(s3Service.getImageUrl(anyString())).thenAnswer(call -> "https://example.com/" + call.getArgument(0));
    when(fileService.moveToDirectory(anyString(), any())).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void publicAlbumListDetailAndYearsStillRequireLogin() throws Exception {
    for (String path : List.of("/api/v1/travel-records", "/api/v1/travel-records/years",
        "/api/v1/travel-records/" + publicRecord.getId())) {
      mockMvc.perform(get(path)).andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.isSuccess").value(false));
    }
  }

  @Test
  void createWithoutVisibilityKeepsLegacyRequestAndReturnsPrivateVersionZero() throws Exception {
    mockMvc.perform(post("/api/v1/travel-records").with(login())
        .contentType(MediaType.APPLICATION_JSON).content(createBody("")))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("COMMON201"))
        .andExpect(jsonPath("$.result.visibility").value("PRIVATE"))
        .andExpect(jsonPath("$.result.version").value(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "\"ALL\"", "\"\""})
  void createRejectsExplicitNullAndInvalidVisibilityWithDomainError(String value) throws Exception {
    mockMvc.perform(post("/api/v1/travel-records").with(login())
        .contentType(MediaType.APPLICATION_JSON).content(createBody(",\"visibility\":" + value)))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRAVEL_RECORD4008"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "true", "{}", "[]"})
  void createRejectsNonStringVisibilityAsMalformedRequest(String value) throws Exception {
    mockMvc.perform(post("/api/v1/travel-records").with(login())
        .contentType(MediaType.APPLICATION_JSON).content(createBody(",\"visibility\":" + value)))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.isSuccess").value(false));
  }

  @Test
  void queryParametersBindToTargetAndVisibilityAndDetailHasExpandedContract() throws Exception {
    mockMvc.perform(get("/api/v1/travel-records").with(login())
        .param("userId", owner.getId().toString()).param("visibility", "PUBLIC").param("year", "2026"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.result.items.length()").value(1))
        .andExpect(jsonPath("$.result.items[0].owner.userId").value(owner.getId()))
        .andExpect(jsonPath("$.result.items[0].participants").isArray())
        .andExpect(jsonPath("$.result.items[0].previewImages").isArray())
        .andExpect(jsonPath("$.result.items[0].stickers").isArray());
    mockMvc.perform(get("/api/v1/travel-records/years").with(login())
        .param("userId", owner.getId().toString()).param("visibility", "PUBLIC"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.result.years[0]").value(2026));
    mockMvc.perform(get("/api/v1/travel-records/" + publicRecord.getId()).with(login()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.result.visibility").value("PUBLIC"))
        .andExpect(jsonPath("$.result.version").value(0))
        .andExpect(jsonPath("$.result.permissions.canEdit").value(false))
        .andExpect(jsonPath("$.result.permissions.canLinkShare").value(true));
  }

  @Test
  void publicReadDoesNotGrantWritePermission() throws Exception {
    mockMvc.perform(patch("/api/v1/travel-records/" + publicRecord.getId()).with(login())
        .contentType(MediaType.APPLICATION_JSON).content(createBody("")))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMON4031"));
    assertThat(entityManager.find(TravelRecord.class, publicRecord.getId()).getTitle()).isEqualTo("공개 여행");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/travel-records", "/api/v1/travel-records/years"})
  void invalidFilterIsRejectedForBothCollectionEndpoints(String path) throws Exception {
    mockMvc.perform(get(path).with(login()).param("visibility", "ALL"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRAVEL_RECORD4008"));
    mockMvc.perform(get(path).with(login()).param("userId", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidYearAndSizeDoNotBecomeServerErrors() throws Exception {
    mockMvc.perform(get("/api/v1/travel-records").with(login()).param("year", "10000"))
        .andExpect(status().isBadRequest());
    for (String size : List.of("-1", "0", "2147483647")) {
      mockMvc.perform(get("/api/v1/travel-records").with(login()).param("size", size))
          .andExpect(status().isBadRequest());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {101, 1000000, Integer.MAX_VALUE})
  void listRejectsOversizedPagesWithDomainError(int size) throws Exception {
    mockMvc.perform(get("/api/v1/travel-records").with(login()).param("size", Integer.toString(size)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TRAVEL_RECORD4006"));
  }

  @Test
  void listAcceptsMaximumPageSize() throws Exception {
    mockMvc.perform(get("/api/v1/travel-records").with(login()).param("size", "100")
        .param("userId", owner.getId().toString()).param("year", "2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.items[0].travelRecordId").value(publicRecord.getId()));
  }

  private RequestPostProcessor login() {
    return authentication(new UsernamePasswordAuthenticationToken(new AuthUser(viewer.getId(), UserRole.USER, "test-session"),
        null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  private User user(String name) {
    User user = User.builder().nickname(name).email(UUID.randomUUID() + "@test.com").gender(Gender.FEMALE)
        .birthYear("2000").region(region).role(UserRole.USER).status(UserStatus.ACTIVE).build();
    entityManager.persist(user);
    return user;
  }

  private String createBody(String visibilityField) {
    return """
        {"title":"부산 여행","regionId":%d,"startDate":"2026-05-22","endDate":"2026-05-24",
         "images":[{"imageKey":"travel-records/test.jpg","imageOrder":1}]%s}
        """.formatted(region.getId(), visibilityField);
  }
}
