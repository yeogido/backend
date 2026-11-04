package com.yeogido.backend.domain.course.dto;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CourseSearchDTO {

  public record Extraction(String region, DurationType durationType, TransportType transportType,
      CompanionType companionType, List<String> terms, Integer totalDays) { }

  public record Interpretation(CourseListReq request, List<String> terms, Integer totalDays) {
    public Interpretation(CourseListReq request, List<String> terms) {
      this(request, terms, null);
    }
  }

  public record DecodedCursor(CourseListReq request, Interpretation interpretation, boolean pinned) { }

  public record CursorPayload(String fingerprint, String cursorValue, Conditions conditions) { }

  public record Conditions(Long regionId, DurationType durationType, TransportType transportType,
      CompanionType companionType, List<String> terms, Integer totalDays) { }
}
