package com.yeogido.backend.domain.course.repository;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.enums.CompanionType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.enums.TransportType;
import java.time.LocalDateTime;
import java.util.List;

public interface CourseQueryRepository {

    List<CourseListRow> findCoursesByCursor(
            CourseReqDTO.CourseListReq request,
            CourseLocation location,
            int limit
    );

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
            String thumbnailKey,
            String title,
            String region,
            DurationType durationType,
            TransportType transportType,
            CompanionType companionType,
            LocalDateTime createdAt,
            Integer recommendOrder,
            Long savedCount,
            Long reviewCount,
            Double distance
    ) { }
}
