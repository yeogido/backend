package com.yeogido.backend.domain.course.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;
import com.yeogido.backend.domain.course.enums.*;
import com.yeogido.backend.domain.course.client.CourseSearchClient;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


class CourseSearchServiceImplTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final CourseSearchClient client = mock(CourseSearchClient.class);
  private final RegionRepository regions = mock(RegionRepository.class);
  private final CourseSearchServiceImpl interpreter;

  CourseSearchServiceImplTest() {
    when(client.isEnabled()).thenReturn(true);
    interpreter = new CourseSearchServiceImpl(client, mapper, regions);
  }

  @Test
  void extractsInternalConditionsWithoutMutatingRequestOrPagination() throws Exception {
    respond("""
        {"supported":true,"totalDays":null,"region":"부산광역시","durationType":"DAY_TRIP","transportType":null,
        "companionType":"COUPLE","terms":["조용","바다"]}
        """);
    when(regions.findAllByNameOrFullName("부산광역시", "부산광역시"))
        .thenReturn(List.of(Region.builder().id(7L).build()));
    CourseListReq request = request("부산에서 연인이랑 당일치기 조용한 바다 여행", null, null, null, null);

    var result = interpreter.interpret(request);

    assertThat(result.request().regionId()).isEqualTo(7L);
    assertThat(result.request().durationType()).isEqualTo(DurationType.DAY_TRIP);
    assertThat(result.request().companionType()).isEqualTo(CompanionType.COUPLE);
    assertThat(result.request().transportType()).isNull();
    assertThat(result.terms()).containsExactly("조용", "바다");
    assertThat(result.extractedKeywords()).containsExactly("부산광역시", "당일치기", "커플", "조용", "바다");
    assertThat(request.regionId()).isNull();
    assertThat(request.durationType()).isNull();
    assertThat(request.keyword()).contains("부산");
    assertThat(result.request().keyword()).isNull();
    assertThat(result.request().sort()).isEqualTo(request.sort());
    assertThat(result.request().cursorId()).isEqualTo(request.cursorId());
    assertThat(result.request().cursorValue()).isEqualTo(request.cursorValue());
    assertThat(result.request().size()).isEqualTo(request.size());
    assertThat(result.request().contentId()).isEqualTo(request.contentId());
    assertThat(result.request().latitude()).isEqualTo(request.latitude());
    assertThat(result.request().longitude()).isEqualTo(request.longitude());
    assertThat(result.request().courseType()).isEqualTo(request.courseType());
  }

  @Test
  void explicitFiltersTakePrecedenceOverEveryInferredFilter() throws Exception {
    respond("""
        {"supported":true,"totalDays":null,"region":"부산광역시","durationType":"DAY_TRIP","transportType":"WALK",
        "companionType":"COUPLE","terms":[]}
        """);
    var result = interpreter.interpret(request("부산 당일 도보 데이트", 2L,
        DurationType.ONE_NIGHT, TransportType.CAR, CompanionType.FAMILY));
    assertThat(result.request().regionId()).isEqualTo(2L);
    assertThat(result.request().durationType()).isEqualTo(DurationType.ONE_NIGHT);
    assertThat(result.request().transportType()).isEqualTo(TransportType.CAR);
    assertThat(result.request().companionType()).isEqualTo(CompanionType.FAMILY);
    assertThat(result.extractedKeywords()).containsExactly("부산광역시", "당일치기", "도보·대중교통", "커플");
    assertThat(result.terms()).isEmpty();
    verifyNoInteractions(regions);
  }

  @Test
  void conditionOnlyQueryDoesNotRetainLiteralSentence() throws Exception {
    respond("""
        {"supported":true,"totalDays":null,"region":null,"durationType":"DAY_TRIP","transportType":null,"companionType":null,"terms":[]}
        """);
    var result = interpreter.interpret(request("당일치기 코스 추천해줘", null, null, null, null));
    assertThat(result.request().durationType()).isEqualTo(DurationType.DAY_TRIP);
    assertThat(result.request().keyword()).isNull();
    assertThat(result.terms()).isEmpty();
  }

  @Test
  void unresolvedRegionFallsBackInsteadOfBroadeningSearch() throws Exception {
    respond("""
        {"supported":true,"totalDays":null,"region":"없는지역","durationType":"DAY_TRIP","transportType":null,"companionType":null,"terms":[]}
        """);
    assertThat(interpreter.interpret(request("없는지역 당일", null, null, null, null))).isNull();
  }

  @Test
  void providerFailureFallsBackToOriginalSearch() {
    when(client.search(anyString(), anyString())).thenThrow(new GeneralException(CourseErrorCode.COURSE_SEARCH_API_ERROR));
    assertThat(interpreter.interpret(request("당일 여행", null, null, null, null))).isNull();
  }

  @Test
  void rejectsMalformedOrUnsafeProviderResponses() throws Exception {
    for (String payload : List.of(
        "{}",
        "{\"supported\":true,\"totalDays\":null,\"region\":null,\"durationType\":\"WEEK\",\"transportType\":null,\"companionType\":null,\"terms\":[]}",
        "{\"supported\":true,\"totalDays\":null,\"region\":null,\"durationType\":null,\"transportType\":null,\"companionType\":null,\"terms\":[]}",
        "{\"supported\":true,\"totalDays\":null,\"region\":null,\"durationType\":null,\"transportType\":null,\"companionType\":null,\"terms\":[null]}",
        "{\"supported\":true,\"totalDays\":null,\"region\":null,\"durationType\":null,\"transportType\":null,\"companionType\":null,\"terms\":[\"\"]}",
        "{\"supported\":true,\"totalDays\":null,\"region\":null,\"durationType\":null,\"transportType\":null,\"companionType\":null,\"terms\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\"]}",
        "{\"supported\":true,\"totalDays\":null,\"region\":null,\"durationType\":\"DAY_TRIP\",\"transportType\":null,\"companionType\":null,\"terms\":[]} {}")) {
      respond(payload);
      assertThat(interpreter.interpret(request("자연어 검색", null, null, null, null))).as(payload).isNull();
    }
  }

  @Test
  void rejectsTruncatedResponseEvenIfJsonIsValid() throws Exception {
    JsonNode response = mapper.valueToTree(Map.of("candidates", List.of(Map.of("finishReason", "MAX_TOKENS",
        "content", Map.of("parts", List.of(Map.of("text", "{}")))))));
    when(client.search(anyString(), anyString())).thenReturn(response);
    assertThat(interpreter.interpret(request("자연어", null, null, null, null))).isNull();
  }

  @Test
  void skipsProviderWhenDisabledBlankOrOverLimit() throws Exception {
    var disabled = new CourseSearchServiceImpl(mock(CourseSearchClient.class), mapper, regions);
    assertThat(disabled.interpret(request("부산", null, null, null, null))).isNull();
    assertThat(interpreter.interpret(request("  ", null, null, null, null))).isNull();
    assertThat(interpreter.interpret(request("가".repeat(501), null, null, null, null))).isNull();
    verify(client, never()).search(anyString(), anyString());
    verifyNoInteractions(regions);
  }

  @Test
  void nullProviderResponseFallsBackToLiteralSearch() {
    when(client.search(anyString(), anyString())).thenReturn(null);
    assertThat(interpreter.interpret(request("바다", null, null, null, null))).isNull();
  }

  @Test
  void ambiguousRegionDoesNotPickAnArbitraryParent() {
    respond("""
        {"supported":true,"totalDays":null,"region":"중구","durationType":"DAY_TRIP","transportType":null,
         "companionType":null,"terms":[]}
        """);
    when(regions.findAllByNameOrFullName("중구", "중구"))
        .thenReturn(List.of(Region.builder().id(1L).build(), Region.builder().id(2L).build()));
    assertThat(interpreter.interpret(request("중구 당일치기", null, null, null, null))).isNull();
  }

  @Test
  void unsupportedConstraintsDoNotApplyPartialPositiveFilters() {
    respond("""
        {"supported":false,"totalDays":null,"region":null,"durationType":"DAY_TRIP","transportType":null,
         "companionType":null,"terms":["바다"]}
        """);
    assertThat(interpreter.interpret(request("바다 빼고 당일치기", null, null, null, null))).isNull();
    verifyNoInteractions(regions);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {4, 6})
  void publicTransportAndLongTripsUseStoredExactDays(int days) {
    respond("""
        {"supported":true,"region":null,"durationType":"THREE_PLUS","transportType":"WALK",
         "companionType":null,"terms":["바다"],"totalDays":%d}
        """.formatted(days));
    var result = interpreter.interpret(request((days - 1) + "박" + days + "일 대중교통 바다", null, null, null, null));
    assertThat(result.request().transportType()).isEqualTo(TransportType.WALK);
    assertThat(result.request().durationType()).isEqualTo(DurationType.THREE_PLUS);
    assertThat(result.totalDays()).isEqualTo(days);
    assertThat(result.terms()).containsExactly("바다");
  }

  @Test
  void explicitDurationTakesPriorityOverConflictingInferredDays() {
    respond("""
        {"supported":true,"region":null,"durationType":"THREE_PLUS","transportType":"WALK",
         "companionType":null,"terms":[],"totalDays":6}
        """);
    var result = interpreter.interpret(request("5박6일 뚜벅이", null, DurationType.DAY_TRIP, null, null));
    assertThat(result.request().durationType()).isEqualTo(DurationType.DAY_TRIP);
    assertThat(result.totalDays()).isNull();
  }

  @Test
  void invalidOrContradictoryDaysFallBackToLiteralSearch() {
    for (String days : List.of("0", "-1", "1.5", "\"6\"", "2147483648", "2")) {
      respond("""
          {"supported":true,"region":null,"durationType":"THREE_PLUS","transportType":null,
           "companionType":null,"terms":[],"totalDays":%s}
          """.formatted(days));
      assertThat(interpreter.interpret(request("5박6일", null, null, null, null))).as(days).isNull();
    }
  }

  @Test
  void threePlusWithoutExactDaysDoesNotAssumeFourDays() {
    respond("""
        {"supported":true,"region":null,"durationType":"THREE_PLUS","transportType":"WALK",
         "companionType":null,"terms":[],"totalDays":null}
        """);
    var result = interpreter.interpret(request("3박 이상 차 없이", null, null, null, null));
    assertThat(result.request().durationType()).isEqualTo(DurationType.THREE_PLUS);
    assertThat(result.totalDays()).isNull();
    assertThat(result.request().transportType()).isEqualTo(TransportType.WALK);
  }

  @Test
  void exactDaysAreNotPartOfPublicListRequest() {
    assertThat(java.util.Arrays.stream(CourseListReq.class.getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain("totalDays");
  }

  @Test
  void displayKeywordsExcludeExplicitOnlyFiltersAndAreDeduplicatedAndImmutable() {
    respond("""
        {"supported":true,"region":null,"durationType":null,"transportType":null,
         "companionType":null,"terms":["바다","바다"],"totalDays":null}
        """);
    var result = interpreter.interpret(request("바다", 2L, DurationType.DAY_TRIP,
        TransportType.CAR, CompanionType.FAMILY));
    assertThat(result.extractedKeywords()).containsExactly("바다");
    assertThatThrownBy(() -> result.extractedKeywords().add("서울"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({"1,DAY_TRIP,당일치기", "2,ONE_NIGHT,1박2일",
      "3,TWO_NIGHT,2박3일", "6,THREE_PLUS,6일"})
  void displayDurationUsesOneReadableExactTag(int days, String duration, String expected) {
    respond("""
        {"supported":true,"region":null,"durationType":"%s","transportType":null,
         "companionType":null,"terms":[],"totalDays":%d}
        """.formatted(duration, days));
    var result = interpreter.interpret(request(days + "일 여행", null, null, null, null));
    assertThat(result.extractedKeywords()).containsExactly(expected);
  }

  @Test
  void unspecifiedLongTripDisplaysRangeWithoutAssumingExactDays() {
    respond("""
        {"supported":true,"region":null,"durationType":"THREE_PLUS","transportType":"CAR",
         "companionType":"PET","terms":[],"totalDays":null}
        """);
    var result = interpreter.interpret(request("3박 이상 자동차 반려동물 여행", null, null, null, null));
    assertThat(result.extractedKeywords()).containsExactly("3박 이상", "자동차", "반려동물");
  }

  private void respond(String payload) {
    JsonNode response = mapper.valueToTree(Map.of("candidates", List.of(Map.of("finishReason", "STOP",
        "content", Map.of("parts", List.of(Map.of("text", payload)))))));
    when(client.search(anyString(), anyString())).thenReturn(response);
  }

  private CourseListReq request(String keyword, Long regionId, DurationType duration,
      TransportType transport, CompanionType companion) {
    return new CourseListReq(CourseType.OFFICIAL, keyword, 9L, regionId, transport, duration, companion,
        CourseSortType.LATEST, BigDecimal.ONE, BigDecimal.TEN, "2026-01-01T00:00:00", 5L, 20);
  }
}
