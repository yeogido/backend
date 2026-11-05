package com.yeogido.backend.domain.course.dto;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.SearchPhase;
import com.yeogido.backend.domain.course.enums.TransportType;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CourseSearchDTO {

    // 자연어에서 추출한 검색 조건
    public record Extraction(
            String region,
            DurationType durationType,
            TransportType transportType,
            CompanionType companionType,
            List<String> terms,
            Integer totalDays
    ) { }

    // 사용자 선택 필터를 반영한 검색 조건과 자연어에서 추출한 표시용 키워드
    public record Interpretation(
            CourseListReq request,
            List<String> terms,
            Integer totalDays,
            List<String> extractedKeywords,
            String semanticSessionId
    ) {
        public Interpretation {
            terms = List.copyOf(terms);
            extractedKeywords = List.copyOf(extractedKeywords);
        }

        public Interpretation(CourseListReq request, List<String> terms, Integer totalDays,
                List<String> extractedKeywords) {
            this(request, terms, totalDays, extractedKeywords, null);
        }

        public Interpretation(CourseListReq request, List<String> terms, Integer totalDays) {
            this(request, terms, totalDays, List.of());
        }

        public Interpretation(CourseListReq request, List<String> terms) {
            this(request, terms, null);
        }
    }

    // 커서에서 복원한 요청, 해석 결과와 현재 검색 단계
    public record DecodedCursor(
            CourseListReq request,
            Interpretation interpretation,
            boolean pinned,
            SearchPhase phase
    ) {
        public DecodedCursor(CourseListReq request, Interpretation interpretation, boolean pinned) {
            this(request, interpretation, pinned, null);
        }
    }

    // 제목 우선 검색의 단계까지 보관하는 nl2 커서
    public record PriorityCursorPayload(
            String fingerprint,
            String cursorValue,
            Conditions conditions,
            SearchPhase phase
    ) { }

    // 의미 검색 후보 스냅샷을 참조하는 nl3 커서
    public record SemanticCursorPayload(
            String fingerprint,
            String cursorValue,
            Conditions conditions,
            SearchPhase phase,
            String semanticSessionId
    ) { }

    // 기존 nl1 커서의 저장 형식
    public record CursorPayload(
            String fingerprint,
            String cursorValue,
            Conditions conditions
    ) { }

    // 페이지 사이에 유지할 검색 조건과 표시용 키워드
    public record Conditions(
            Long regionId,
            DurationType durationType,
            TransportType transportType,
            CompanionType companionType,
            List<String> terms,
            Integer totalDays,
            List<String> extractedKeywords
    ) { }
}
