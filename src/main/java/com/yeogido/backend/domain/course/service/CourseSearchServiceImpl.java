package com.yeogido.backend.domain.course.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.course.client.CourseSearchClient;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.Extraction;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.Interpretation;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.global.exception.GeneralException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class CourseSearchServiceImpl implements CourseSearchService {

  private final CourseSearchClient courseSearchClient;
  private final ObjectMapper objectMapper;
  private final RegionRepository regionRepository;
  private final String instructions;

  public CourseSearchServiceImpl(CourseSearchClient courseSearchClient, ObjectMapper objectMapper,
      RegionRepository regionRepository) {
    this.courseSearchClient = courseSearchClient;
    this.objectMapper = objectMapper;
    this.regionRepository = regionRepository;
    try {
      this.instructions = courseSearchClient.isEnabled()
          ? new ClassPathResource("prompts/course-search.txt").getContentAsString(StandardCharsets.UTF_8) : "";
    } catch (IOException exception) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
    }
  }

  // 해석 불가 시 null 반환을 통한 기존 문자열 검색 적용
  @Override
  public Interpretation interpret(CourseListReq request) {
    if (!StringUtils.hasText(request.keyword()) || request.keyword().length() > 500 || !courseSearchClient.isEnabled()) {
      return null;
    }
    try {
      String input = objectMapper.writeValueAsString(Map.of("query", request.keyword()));
      Extraction extraction = parseResponse(courseSearchClient.search(instructions, input), request.keyword());
      return toInterpretation(request, extraction);
    } catch (GeneralException exception) {
      // 검색 문장과 인증 정보 및 제공자 응답의 로그 노출 방지
      String failureType = exception.getErrorCode() == CourseErrorCode.INVALID_COURSE_SEARCH_RESPONSE
          ? "response_validation_error" : "generation_api_error";
      log.warn("자연어 검색 해석 실패에 따른 기존 문자열 검색 적용. failureType={}, code={}",
          failureType, exception.getErrorCode().getCode());
      return null;
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      log.warn("자연어 검색 응답 검증 실패에 따른 기존 문자열 검색 적용. failureType=response_parse_error, code={}",
          CourseErrorCode.INVALID_COURSE_SEARCH_RESPONSE.getCode());
      return null;
    }
  }

  private Extraction parseResponse(JsonNode response, String query) throws JsonProcessingException {
    require(response != null);
    JsonNode candidates = response.path("candidates");
    require(candidates.isArray() && candidates.size() == 1);
    JsonNode candidate = candidates.get(0);
    require("STOP".equals(candidate.path("finishReason").asText()));
    JsonNode parts = candidate.path("content").path("parts");
    require(parts.isArray() && !parts.isEmpty());
    StringBuilder text = new StringBuilder();
    for (JsonNode part : parts) {
      if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) {
        text.append(part.path("text").textValue());
      }
    }
    JsonNode output = objectMapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text.toString());
    require(output != null && output.isObject() && output.size() == 7);
    for (String field : List.of("supported", "region", "durationType", "transportType", "companionType", "terms", "totalDays")) {
      require(output.has(field));
    }
    // 미지원 조건의 누락이나 긍정 조건으로의 부분 변환 방지
    require(output.get("supported").isBoolean() && output.get("supported").booleanValue());
    JsonNode termsNode = output.get("terms");
    require(termsNode.isArray() && termsNode.size() <= 6);
    List<String> terms = new ArrayList<>();
    for (JsonNode term : termsNode) {
      String value = nullableText(term);
      require(value != null);
      terms.add(value);
    }
    Integer totalDays = nullablePositiveInteger(output.get("totalDays"));
    DurationType duration = enumValue(DurationType.class, output.get("durationType"));
    if (query.matches("(?s).*\\b[0-9]+\\s*박\\s*(?:이상|부터|~|∼).*")) {
      require(totalDays == null);
    }
    if (totalDays != null) {
      require(duration == null || duration == durationForDays(totalDays));
      duration = durationForDays(totalDays);
    }
    return new Extraction(nullableText(output.get("region")),
        duration,
        enumValue(TransportType.class, output.get("transportType")),
        enumValue(CompanionType.class, output.get("companionType")), terms.stream().distinct().toList(), totalDays);
  }

  private Interpretation toInterpretation(CourseListReq request, Extraction extraction) {
    require(extraction.region() != null || extraction.durationType() != null || extraction.transportType() != null
        || extraction.companionType() != null || extraction.totalDays() != null || !extraction.terms().isEmpty());
    Long regionId = request.regionId();
    String regionName = null;
    if (extraction.region() != null) {
      List<Region> regions = regionRepository.findAllByNameOrFullName(extraction.region(), extraction.region());
      // 중복 지역명의 임의 선택 및 지역 조건의 무조건 해제 방지
      require(regions.size() == 1);
      Region region = regions.get(0);
      regionName = region.getName();
      if (regionId == null) {
        regionId = region.getId();
      }
    }
    DurationType duration = request.durationType() != null ? request.durationType() : extraction.durationType();
    Integer totalDays = extraction.totalDays();
    if (request.durationType() != null && totalDays != null
        && durationForDays(totalDays) != request.durationType()) {
      // 사용자 선택 기간과 충돌하는 자연어 일수 조건의 적용 방지
      totalDays = null;
    }
    CourseListReq effective = new CourseListReq(request.courseType(), null, request.contentId(), regionId,
        request.transportType() != null ? request.transportType() : extraction.transportType(),
        duration,
        request.companionType() != null ? request.companionType() : extraction.companionType(),
        request.sort(), request.latitude(), request.longitude(), request.cursorValue(), request.cursorId(), request.size());
    return new Interpretation(effective, extraction.terms(), totalDays, displayKeywords(extraction, regionName));
  }

  private static List<String> displayKeywords(Extraction extraction, String regionName) {
    List<String> keywords = new ArrayList<>();
    if (regionName != null) {
      keywords.add(regionName);
    }
    if (extraction.totalDays() != null) {
      keywords.add(switch (extraction.totalDays()) {
        case 1 -> "당일치기";
        case 2 -> "1박2일";
        case 3 -> "2박3일";
        default -> extraction.totalDays() + "일";
      });
    } else if (extraction.durationType() != null) {
      keywords.add(extraction.durationType().getDisplayName());
    }
    if (extraction.transportType() != null) {
      keywords.add(extraction.transportType() == TransportType.WALK ? "도보·대중교통" : "자동차");
    }
    if (extraction.companionType() != null) {
      keywords.add(switch (extraction.companionType()) {
        case SOLO -> "혼자";
        case FRIEND -> "친구";
        case COUPLE -> "커플";
        case FAMILY -> "가족";
        case PET -> "반려동물";
      });
    }
    keywords.addAll(extraction.terms());
    return keywords.stream().distinct().toList();
  }

  private static Integer nullablePositiveInteger(JsonNode value) {
    if (value.isNull()) {
      return null;
    }
    require(value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 1);
    return value.intValue();
  }

  private static DurationType durationForDays(int totalDays) {
    return switch (totalDays) {
      case 1 -> DurationType.DAY_TRIP;
      case 2 -> DurationType.ONE_NIGHT;
      case 3 -> DurationType.TWO_NIGHT;
      default -> DurationType.THREE_PLUS;
    };
  }

  private static String nullableText(JsonNode value) {
    if (value.isNull()) {
      return null;
    }
    require(value.isTextual() && StringUtils.hasText(value.textValue()) && value.textValue().length() <= 100);
    return value.textValue().trim();
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode value) {
    String text = nullableText(value);
    return text == null ? null : Enum.valueOf(type, text);
  }

  private static void require(boolean condition) {
    if (!condition) {
      throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_RESPONSE);
    }
  }
}
