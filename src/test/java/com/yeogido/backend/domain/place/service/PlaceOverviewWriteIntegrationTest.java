package com.yeogido.backend.domain.place.service;

import com.yeogido.backend.domain.content.dto.ContentReqDTO;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceOverviewStatus;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceOverviewSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.global.config.JpaConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true"})
@Import({JpaConfig.class, PlaceServiceImpl.class})
@ActiveProfiles("test")
class PlaceOverviewWriteIntegrationTest {
  @Autowired PlaceService service;
  @Autowired PlaceRepository places;
  @Autowired RegionRepository regions;
  @Autowired EntityManager entityManager;
  Region district;

  @BeforeEach
  void seedRegion() {
    var province = regions.save(Region.builder().name("서울").type(RegionType.REGION).build());
    district = regions.save(Region.builder().name("마포구").parent(province)
        .type(RegionType.SUB_REGION).build());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void newPlaceBecomesPendingBatchCandidate(boolean course) {
    Place saved = write(course, "장소", "서울 마포구 테스트로 1", "서울 마포구 테스트동 1",
        new BigDecimal("37.5"));
    entityManager.flush();
    entityManager.clear();
    var found = places.findById(saved.getId()).orElseThrow();
    assertThat(found.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.PENDING);
    assertThat(found.getOverviewAttemptCount()).isZero();
    assertThat(places.findOverviewPendingCandidates(2, PageRequest.of(0, 20))).contains(found.getId());
  }

  @ParameterizedTest
  @CsvSource({"true,COMPLETED", "false,COMPLETED", "true,FAILED", "false,FAILED",
      "true,PROCESSING", "false,PROCESSING", "true,PENDING", "false,PENDING",
      "true,NO_OVERVIEW", "false,NO_OVERVIEW"})
  void existingPlacePreservesMetadataAndOverviewDespiteChangedRequest(
      boolean course, PlaceOverviewStatus status) {
    var place = existing(status);
    var attemptedAt = place.getOverviewAttemptedAt();
    Long id = place.getId();
    // 기존 장소는 요청 주소를 다시 해석하지 않고 그대로 재사용해야 한다.
    var reused = write(course, "변경된 장소", "존재하지 않는 주소", "변경된 지번 주소",
        new BigDecimal("37.6"));
    assertThat(reused).isSameAs(place);
    entityManager.flush();
    entityManager.clear();
    var found = places.findById(id).orElseThrow();
    assertThat(places.count()).isEqualTo(1);
    assertThat(found.getName()).isEqualTo("장소");
    assertThat(found.getRegion().getId()).isEqualTo(district.getId());
    assertThat(found.getRoadAddress()).isEqualTo("서울 마포구 테스트로 1");
    assertThat(found.getLotAddress()).isEqualTo("서울 마포구 테스트동 1");
    assertThat(found.getLatitude()).isEqualByComparingTo("37.5");
    assertThat(found.getLongitude()).isEqualByComparingTo("127.0");
    assertThat(found.getOverview()).isEqualTo(status == PlaceOverviewStatus.COMPLETED ? "기존 설명" : null);
    assertThat(found.getOverviewSource()).isEqualTo(
        status == PlaceOverviewStatus.COMPLETED ? PlaceOverviewSource.TOUR_API : null);
    assertThat(found.getOverviewStatus()).isEqualTo(status);
    assertThat(found.getOverviewAttemptCount()).isEqualTo(2);
    assertThat(found.getOverviewAttemptedAt()).isEqualTo(attemptedAt);
  }

  private Place existing(PlaceOverviewStatus status) {
    return places.saveAndFlush(Place.builder().externalPlaceId("fixture").source(PlaceSource.KAKAO)
        .region(district).name("장소").roadAddress("서울 마포구 테스트로 1").lotAddress("서울 마포구 테스트동 1")
        .latitude(new BigDecimal("37.5")).longitude(new BigDecimal("127.0"))
        .overview(status == PlaceOverviewStatus.COMPLETED ? "기존 설명" : null)
        .overviewSource(status == PlaceOverviewStatus.COMPLETED ? PlaceOverviewSource.TOUR_API : null)
        .overviewStatus(status).overviewAttemptCount(2)
        .overviewAttemptedAt(LocalDateTime.of(2026, 11, 2, 12, 0)).build());
  }

  private Place write(boolean course, String name, String road, String lot, BigDecimal latitude) {
    if (course) {
      var request = new CourseReqDTO.CourseItemCreateReq(1, CourseItemType.PLACE, null, "fixture",
          "AT4", name, road, lot, latitude, new BigDecimal("127.0"), null);
      return service.getOrCreatePlace(request, new HashMap<>(service.getPlaceMap(java.util.List.of(request))));
    }
    return service.getOrCreatePlace(new ContentReqDTO.PlaceReq("fixture", PlaceSource.KAKAO,
        name, road, lot, latitude, new BigDecimal("127.0")));
  }
}
