package com.yeogido.backend.domain.course.validation;

import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.enums.CourseItemType;
import com.yeogido.backend.domain.course.enums.DurationType;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CourseScheduleValidator {

    private CourseScheduleValidator() { }

    public static void validateCreate(CourseReqDTO.CourseCreateReq request) {
        validateDuration(request.durationType(), request.totalDays());
        validateItems(request.courseItems(), request.totalDays());
    }

    public static void validateUpdate(Course course, CourseReqDTO.CourseUpdateReq request) {
        boolean durationChanged = request.durationType() != null
                && request.durationType() != course.getDurationType();
        boolean daysChanged = request.totalDays() != null
                && !Objects.equals(request.totalDays(), course.getTotalDays());
        // 기간 유형 변경 시 전체 일수 필수, 기간 유형 또는 일수 변경 시 전체 일정 필수
        if ((durationChanged && request.totalDays() == null)
                || ((durationChanged || daysChanged) && request.courseItems() == null)) {
            throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
        }
        // 모든 장소가 1일차에 배치된 기존 코스의 일반 정보 수정 허용
        if (request.courseItems() == null && !durationChanged && !daysChanged) {
            return;
        }
        DurationType duration = request.durationType() == null ? course.getDurationType() : request.durationType();
        Integer totalDays = request.totalDays() == null ? course.getTotalDays() : request.totalDays();
        validateDuration(duration, totalDays);
        validateItems(request.courseItems(), totalDays);
    }

    private static void validateDuration(DurationType duration, Integer totalDays) {
        if (duration == null || totalDays == null || totalDays < 1) {
            throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
        }
        boolean valid = switch (duration) {
            case DAY_TRIP -> totalDays == 1;
            case ONE_NIGHT -> totalDays == 2;
            case TWO_NIGHT -> totalDays == 3;
            case THREE_PLUS -> totalDays >= 4;
        };
        if (!valid) {
            throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
        }
    }

    private static void validateItems(List<CourseReqDTO.CourseItemCreateReq> items, int totalDays) {
        if (items == null || items.isEmpty()) {
            throw new GeneralException(CourseErrorCode.PLACE_ITEM_REQUIRED);
        }
        Map<Integer, Set<Integer>> ordersByDay = new HashMap<>();
        Set<Integer> daysWithPlace = new HashSet<>();
        for (var item : items) {
            if (item == null || item.day() == null || item.day() < 1 || item.day() > totalDays) {
                throw new GeneralException(GeneralErrorCode.INVALID_REQUEST);
            }
            if (item.order() == null || item.order() < 1 || item.type() == null) {
                throw new GeneralException(CourseErrorCode.INVALID_COURSE_ITEM);
            }
            Set<Integer> orders = ordersByDay.computeIfAbsent(item.day(), ignored -> new HashSet<>());
            if (!orders.add(item.order())) {
                throw new GeneralException(CourseErrorCode.DUPLICATE_COURSE_ITEM_ORDER);
            }
            // 각 일차 첫 항목의 이전 이동시간 입력 금지
            if (item.order() == 1 && item.timesFromPrevious() != null && !item.timesFromPrevious().isEmpty()) {
                throw new GeneralException(CourseErrorCode.INVALID_COURSE_ITEM);
            }
            if (item.type() == CourseItemType.PLACE) {
                daysWithPlace.add(item.day());
            }
        }
        // 행사 포함 여부와 무관하게 모든 일차에 장소가 최소 하나 이상 있는지 검증
        if (daysWithPlace.size() != totalDays) {
            throw new GeneralException(CourseErrorCode.PLACE_ITEM_REQUIRED);
        }
        // 양수·중복 검증 이후 각 순서가 항목 수 이하이면 1부터 연속된 순서 보장
        for (Set<Integer> orders : ordersByDay.values()) {
            if (orders.stream().anyMatch(order -> order > orders.size())) {
                throw new GeneralException(CourseErrorCode.INVALID_COURSE_ITEM);
            }
        }
    }
}
