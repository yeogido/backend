package com.yeogido.backend.domain.course.converter;

import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseSortType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.DecodedCursor;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.Interpretation;
import com.yeogido.backend.domain.course.enums.SearchPhase;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class CourseSearchCursorConverterTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final CourseSearchCursorConverter cursor = new CourseSearchCursorConverter(mapper);

  @Test
  void preservesInterpretationAndOriginalRequestAcrossPages() {
    var original = request("부산 당일치기 바다", null, null, 20, null, null);
    var interpreted = new Interpretation(new CourseListReq(CourseType.OFFICIAL, null, null, 7L,
        TransportType.WALK, DurationType.DAY_TRIP, CompanionType.COUPLE, CourseSortType.RECOMMEND,
        null, null, null, null, 20), List.of("바다"));
    String token = cursor.encode(original, interpreted, "3");
    var next = request(original.keyword(), token, 99L, 5, null, null);

    var decoded = cursor.decode(next);

    assertThat(decoded.pinned()).isTrue();
    assertThat(decoded.request().keyword()).isEqualTo(original.keyword());
    assertThat(decoded.request().cursorValue()).isEqualTo("3");
    assertThat(decoded.request().cursorId()).isEqualTo(99L);
    assertThat(decoded.interpretation().request().regionId()).isEqualTo(7L);
    assertThat(decoded.interpretation().request().durationType()).isEqualTo(DurationType.DAY_TRIP);
    assertThat(decoded.interpretation().request().transportType()).isEqualTo(TransportType.WALK);
    assertThat(decoded.interpretation().request().companionType()).isEqualTo(CompanionType.COUPLE);
    assertThat(decoded.interpretation().request().cursorValue()).isEqualTo("3");
    assertThat(decoded.interpretation().request().cursorId()).isEqualTo(99L);
    assertThat(decoded.interpretation().request().size()).isEqualTo(5);
    assertThat(decoded.interpretation().request().keyword()).isNull();
    assertThat(decoded.interpretation().terms()).containsExactly("바다");
  }

  @Test
  void pinsLiteralFallbackAndSupportsNullRecommendOrderCursor() {
    var original = request("부산 당일치기", null, null, 20, null, null);
    String token = cursor.encode(original, null, null);
    var decoded = cursor.decode(request(original.keyword(), token, 9L, 20, null, null));

    assertThat(decoded.pinned()).isTrue();
    assertThat(decoded.interpretation()).isNull();
    assertThat(decoded.request().keyword()).isEqualTo(original.keyword());
    assertThat(decoded.request().cursorValue()).isNull();
  }

  @Test
  void passesLegacyAndFirstPageCursorsThroughWithoutPinning() {
    for (String value : new String[] {null, "3", "2026-11-03T12:00:00"}) {
      var request = request("바다", value, null, 20, null, null);
      assertThat(cursor.decode(request)).isEqualTo(new DecodedCursor(request, null, false));
    }
  }

  @Test
  void rejectsTokenReusedForDifferentQueryOrExplicitFilter() {
    var original = request("바다", null, null, 20, null, null);
    String token = cursor.encode(original, null, "3");
    assertInvalid(request("산", token, 9L, 20, null, null));
    assertInvalid(request("바다", token, 9L, 20, 7L, null));
    assertInvalid(request("바다", token, 9L, 20, null, DurationType.DAY_TRIP));
    assertInvalid(new CourseListReq(CourseType.OFFICIAL, "바다", null, null, null, null, null,
        CourseSortType.LATEST, null, null, token, 9L, 20));
    assertInvalid(new CourseListReq(CourseType.OFFICIAL, "바다", null, null, null, null, null,
        CourseSortType.RECOMMEND, new BigDecimal("35.0"), null, token, 9L, 20));
    assertInvalid(new CourseListReq(CourseType.OFFICIAL, "바다", 88L, null, null, null, null,
        CourseSortType.RECOMMEND, null, null, token, 9L, 20));
  }

  @Test
  void rejectsMalformedOversizedOrPositionlessTokens() {
    assertInvalid(request("바다", "nl1.not valid base64!", 9L, 20, null, null));
    assertInvalid(request("바다", "nl1." + "a".repeat(8192), 9L, 20, null, null));
    String token = cursor.encode(request("바다", null, null, 20, null, null), null, "1");
    assertInvalid(request("바다", token, null, 20, null, null));
  }

  @Test
  void rejectsTamperedConditionsOverridingExplicitSelections() throws Exception {
    var original = request("바다", null, null, 20, 7L, DurationType.DAY_TRIP);
    var interpretation = new Interpretation(original, List.of("바다"));
    String token = cursor.encode(original, interpretation, "1");
    ObjectNode payload = payload(token);
    ((ObjectNode) payload.get("conditions")).put("regionId", 8);
    assertInvalid(request("바다", token(payload), 9L, 20, 7L, DurationType.DAY_TRIP));
    payload = payload(token);
    ((ObjectNode) payload.get("conditions")).put("durationType", "ONE_NIGHT");
    assertInvalid(request("바다", token(payload), 9L, 20, 7L, DurationType.DAY_TRIP));
  }

  @Test
  void rejectsInvalidTermsEnumsAndUnknownFields() throws Exception {
    var original = request("바다", null, null, 20, null, null);
    String token = cursor.encode(original, new Interpretation(original, List.of("바다")), "1");
    ObjectNode payload = payload(token);
    ((ObjectNode) payload.get("conditions")).putArray("terms").add(" ");
    assertInvalid(request("바다", token(payload), 9L, 20, null, null));
    payload = payload(token);
    var terms = ((ObjectNode) payload.get("conditions")).putArray("terms");
    for (int i = 0; i < 7; i++) terms.add("바다");
    assertInvalid(request("바다", token(payload), 9L, 20, null, null));
    payload = payload(token);
    ((ObjectNode) payload.get("conditions")).putArray("terms").add("a".repeat(101));
    assertInvalid(request("바다", token(payload), 9L, 20, null, null));
    payload = payload(token);
    ((ObjectNode) payload.get("conditions")).put("durationType", "MADE_UP");
    assertInvalid(request("바다", token(payload), 9L, 20, null, null));
    payload = payload(token);
    payload.put("unknown", true);
    assertInvalid(request("바다", token(payload), 9L, 20, null, null));
  }

  @Test
  void reportsCursorEncodingFailureAsServerError() throws Exception {
    ObjectMapper failingMapper = Mockito.mock(ObjectMapper.class);
    Mockito.when(failingMapper.writeValueAsString(ArgumentMatchers.any()))
        .thenThrow(new JsonProcessingException("serialization failed") { });
    var converter = new CourseSearchCursorConverter(failingMapper);

    assertThatThrownBy(() -> converter.encode(request("바다", null, null, 20, null, null), null, "1"))
        .isInstanceOfSatisfying(GeneralException.class,
            exception -> assertThat(exception.getErrorCode())
                .isEqualTo(CourseErrorCode.COURSE_SEARCH_CURSOR_ENCODING_FAILED));
  }

  @Test
  void preservesExactLongTripDaysAcrossPages() {
    var original = request("5박6일 대중교통 바다", null, null, 20, null, null);
    var effective = new CourseListReq(CourseType.OFFICIAL, null, null, null, TransportType.WALK,
        DurationType.THREE_PLUS, null, CourseSortType.RECOMMEND, null, null, null, null, 20);
    String token = cursor.encode(original, new Interpretation(effective, List.of("바다"), 6), "1");
    var decoded = cursor.decode(request(original.keyword(), token, 8L, 20, null, null));
    assertThat(decoded.interpretation().totalDays()).isEqualTo(6);
    assertThat(decoded.interpretation().request().transportType()).isEqualTo(TransportType.WALK);
    assertThat(decoded.interpretation().request().durationType()).isEqualTo(DurationType.THREE_PLUS);
  }

  @Test
  void rejectsNonpositiveInternalDays() throws Exception {
    var original = request("5박6일 바다", null, null, 20, null, null);
    String token = cursor.encode(original, new Interpretation(original, List.of("바다"), 6), "1");
    for (int days : new int[] {0, -1}) {
      ObjectNode payload = payload(token);
      ((ObjectNode) payload.get("conditions")).put("totalDays", days);
      assertInvalid(request(original.keyword(), token(payload), 8L, 20, null, null));
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(SearchPhase.class)
  void priorityCursorPreservesBucketConditionsAndNullRecommendPosition(SearchPhase phase) {
    var original = request("5박6일 바다", null, null, 20, null, null);
    String token = cursor.encodePriority(original,
        new Interpretation(original, List.of("바다"), 6), null, phase);
    assertThat(token).startsWith("nl2.");
    var decoded = cursor.decode(request(original.keyword(), token, 8L, 3, null, null));
    assertThat(decoded.phase()).isEqualTo(phase);
    assertThat(decoded.interpretation().totalDays()).isEqualTo(6);
    assertThat(decoded.request().cursorValue()).isNull();
    assertThat(decoded.request().size()).isEqualTo(3);
    assertInvalid(request("산", token, 8L, 3, null, null));
  }

  @Test
  void priorityCursorRejectsMissingOrInvalidBucket() throws Exception {
    var original = request("바다", null, null, 20, null, null);
    String token = cursor.encodePriority(original, new Interpretation(original, List.of("바다")), "1", SearchPhase.TITLE);
    for (String phase : new String[] {null, "UNKNOWN"}) {
      ObjectNode payload = payload(token);
      if (phase == null) payload.remove("phase");
      else payload.put("phase", phase);
      String changed = "nl2." + token(payload).substring(4);
      assertInvalid(request("바다", changed, 8L, 20, null, null));
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  void displayKeywordsRoundTripAndOldCursorsMayOmitOnlyTheOptionalField(boolean priority) throws Exception {
    var original = request("5박6일 바다", null, null, 20, null, null);
    var interpretation = new Interpretation(original, List.of("바다"), 6, List.of("6일", "바다"));
    String encoded = priority ? cursor.encodePriority(original, interpretation, "1", SearchPhase.NATURAL)
        : cursor.encode(original, interpretation, "1");
    var decoded = cursor.decode(request(original.keyword(), encoded, 8L, 20, null, null));
    assertThat(decoded.interpretation().extractedKeywords()).containsExactly("6일", "바다");
    ObjectNode old = payload(encoded);
    ((ObjectNode) old.get("conditions")).remove("extractedKeywords");
    String oldToken = encoded.substring(0, 4) + token(old).substring(4);
    assertThat(cursor.decode(request(original.keyword(), oldToken, 8L, 20, null, null))
        .interpretation().extractedKeywords()).isEmpty();
    ((ObjectNode) old.get("conditions")).remove("terms");
    assertInvalid(request(original.keyword(), encoded.substring(0, 4) + token(old).substring(4), 8L, 20, null, null));
  }

  @Test
  void rejectsMalformedDisplayKeywordsWithoutCoercingValuesToStrings() throws Exception {
    var original = request("바다", null, null, 20, null, null);
    String encoded = cursor.encodePriority(original, new Interpretation(original, List.of("바다")), "1", SearchPhase.TITLE);
    for (String json : List.of("null", "{}", "123", "[123]", "[true]", "[null]", "[\" \"]", "[\"바다\",\"바다\"]",
        mapper.writeValueAsString(List.of("a".repeat(101))), mapper.writeValueAsString(java.util.Collections.nCopies(13, "바다")))) {
      ObjectNode changed = payload(encoded);
      ((ObjectNode) changed.get("conditions")).set("extractedKeywords", mapper.readTree(json));
      assertInvalid(request(original.keyword(), "nl2." + token(changed).substring(4), 8L, 20, null, null));
    }
  }

  private void assertInvalid(CourseListReq request) {
    assertThatThrownBy(() -> cursor.decode(request)).isInstanceOfSatisfying(GeneralException.class,
        exception -> assertThat(exception.getErrorCode()).isEqualTo(CourseErrorCode.INVALID_COURSE_SEARCH_CURSOR));
  }

  private ObjectNode payload(String token) throws Exception {
    return (ObjectNode) mapper.readTree(Base64.getUrlDecoder().decode(token.substring(4)));
  }

  private String token(ObjectNode payload) throws Exception {
    return "nl1." + Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(payload));
  }

  private CourseListReq request(String keyword, String value, Long id, int size, Long region, DurationType duration) {
    return new CourseListReq(CourseType.OFFICIAL, keyword, null, region, null, duration, null,
        CourseSortType.RECOMMEND, null, null, value, id, size);
  }
}
