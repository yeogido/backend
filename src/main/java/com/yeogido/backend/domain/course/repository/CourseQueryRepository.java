package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CourseQueryRepository {

    List<CourseListRow> findCoursesByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit
    );

    List<CourseListRow> findCoursesByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit,
            List<String> searchTerms
    );

    List<CourseListRow> findCoursesByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit,
            List<String> searchTerms,
            Integer totalDays
    );

    List<CourseListRow> findSearchPhaseByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit,
            List<String> searchTerms,
            Integer totalDays,
            String originalKeyword,
            boolean titlePhase
    );

    List<CourseListRow> findSemanticCoursesByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit,
            Integer totalDays,
            String originalKeyword,
            List<Long> candidateIds
    );

    List<CourseListRow> findSearchPhaseByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit,
            List<String> searchTerms,
            Integer totalDays,
            String originalKeyword,
            boolean titlePhase,
            SearchMembership exclusion
    );

    List<CourseListRow> findSemanticCoursesByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            Map<Long, Long> popularityScores,
            int limit,
            Integer totalDays,
            String originalKeyword,
            List<Long> candidateIds,
            SearchMembership exclusion
    );

    SearchMembership findSearchMembership(
            CourseReqDTO.CourseListReq originalRequest,
            CourseReqDTO.CourseListReq naturalRequest,
            List<String> terms,
            Integer totalDays,
            List<Long> semanticCandidateIds,
            CourseLocation location,
            Map<Long, Long> popularityScores
    );

    List<CourseListRow> findPinnedSearchRows(List<Long> memberIds);

    record SearchMembership(List<Long> titleIds, List<Long> naturalIds) {
        public SearchMembership {
            titleIds = List.copyOf(titleIds);
            naturalIds = List.copyOf(naturalIds);
        }
    }

    record CourseLocation(
            Double latitude,
            Double longitude
    ) {
        boolean exists() {
            return latitude != null && longitude != null;
        }
    }

    record CourseListRow(
            Long courseId,
            CourseType courseType,
            Long authorUserId,
            String thumbnailKey,
            String routeImageKey,
            String title,
            String region,
            DurationType durationType,
            TransportType transportType,
            CompanionType companionType,
            LocalDateTime createdAt,
            Integer recommendOrder,
            Long savedCount,
            Long reviewCount,
            Double distance,
            Long popularityScore
    ) { }
}
