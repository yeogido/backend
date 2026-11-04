package com.yeogido.backend.domain.course.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.Conditions;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.CursorPayload;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.DecodedCursor;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.Interpretation;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 페이지 간 검색 조건 유지를 위함
@Component
public class CourseSearchCursorConverter {
  private static final String PREFIX = "nl1.";
  private static final int MAX_LENGTH = 8192;
  private final ObjectMapper mapper;

  public CourseSearchCursorConverter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public DecodedCursor decode(CourseListReq original) {
    String value = original.cursorValue();
    if (value == null || !value.startsWith(PREFIX)) {
      return new DecodedCursor(original, null, false);
    }
    try {
      require(value.length() <= MAX_LENGTH && original.cursorId() != null);
      CursorPayload payload = mapper.readerFor(CursorPayload.class)
          .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
          .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
              DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
              DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
              DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
          .readValue(Base64.getUrlDecoder().decode(value.substring(PREFIX.length())));
      require(payload != null && fingerprint(original).equals(payload.fingerprint()));
      require(payload.cursorValue() == null || payload.cursorValue().length() <= 100);
      CourseListReq request = copy(original, original.keyword(), original.regionId(), original.durationType(),
          original.transportType(), original.companionType(), payload.cursorValue());
      Conditions conditions = payload.conditions();
      if (conditions == null) {
        return new DecodedCursor(request, null, true);
      }
      require(conditions.regionId() == null || conditions.regionId() > 0);
      require(conditions.totalDays() == null || conditions.totalDays() >= 1);
      require(conditions.terms() != null && conditions.terms().size() <= 6);
      for (String term : conditions.terms()) {
        require(StringUtils.hasText(term) && term.length() <= 100);
      }
      require(conditions.regionId() != null || conditions.durationType() != null
          || conditions.transportType() != null || conditions.companionType() != null
          || conditions.totalDays() != null || !conditions.terms().isEmpty());
      // 커서에 의한 사용자 선택 필터 덮어쓰기 방지
      requireUnchanged(original.regionId(), conditions.regionId());
      requireUnchanged(original.durationType(), conditions.durationType());
      requireUnchanged(original.transportType(), conditions.transportType());
      requireUnchanged(original.companionType(), conditions.companionType());
      CourseListReq effective = copy(original, null, conditions.regionId(), conditions.durationType(),
          conditions.transportType(), conditions.companionType(), payload.cursorValue());
      return new DecodedCursor(request, new Interpretation(effective, List.copyOf(conditions.terms()), conditions.totalDays()), true);
    } catch (Exception exception) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CURSOR);
    }
  }

  public String encode(CourseListReq original, Interpretation interpretation, Object actualCursorValue) {
    try {
      Conditions conditions = interpretation == null ? null : new Conditions(
          interpretation.request().regionId(), interpretation.request().durationType(),
          interpretation.request().transportType(), interpretation.request().companionType(),
          interpretation.terms(), interpretation.totalDays());
      CursorPayload payload = new CursorPayload(fingerprint(original),
          actualCursorValue == null ? null : actualCursorValue.toString(), conditions);
      String value = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(payload));
      require(value.length() <= MAX_LENGTH);
      return value;
    } catch (Exception exception) {
      throw new GeneralException(CourseErrorCode.COURSE_SEARCH_CURSOR_ENCODING_FAILED);
    }
  }

  private String fingerprint(CourseListReq request) throws Exception {
    // 커서 위치와 페이지 크기를 제외한 검색 조건 식별값 생성
    CourseListReq canonical = new CourseListReq(request.courseType(), request.keyword(), request.contentId(),
        request.regionId(), request.transportType(), request.durationType(), request.companionType(),
        request.sort(), request.latitude(), request.longitude(), null, null, 20);
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(
        mapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }

  private static CourseListReq copy(CourseListReq original, String keyword, Long regionId, DurationType duration,
      TransportType transport, CompanionType companion, String cursorValue) {
    return new CourseListReq(original.courseType(), keyword, original.contentId(), regionId, transport,
        duration, companion, original.sort(), original.latitude(), original.longitude(), cursorValue,
        original.cursorId(), original.size());
  }

  private static void requireUnchanged(Object explicit, Object pinned) {
    require(explicit == null || Objects.equals(explicit, pinned));
  }

  private static void require(boolean condition) {
    if (!condition) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CURSOR);
    }
  }

}
