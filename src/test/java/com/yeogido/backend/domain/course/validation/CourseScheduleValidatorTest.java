package com.yeogido.backend.domain.course.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseItemCreateReq;
import com.yeogido.backend.domain.course.entity.Course;
import com.yeogido.backend.domain.course.enums.*;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.global.exception.ErrorCode;
import com.yeogido.backend.global.exception.GeneralErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import jakarta.validation.Validation;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CourseScheduleValidatorTest {

    @ParameterizedTest
    @CsvSource({"DAY_TRIP,1", "ONE_NIGHT,2", "TWO_NIGHT,3", "THREE_PLUS,4", "THREE_PLUS,6"})
    void acceptsValidDurationsAndRepeatedOrdersAcrossDays(DurationType duration, int days) {
        assertThatCode(() -> CourseScheduleValidator.validateCreate(create(duration, days, places(days))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"DAY_TRIP,2", "ONE_NIGHT,1", "TWO_NIGHT,4", "THREE_PLUS,3", "THREE_PLUS,0"})
    void rejectsDurationMismatch(DurationType duration, int days) {
        expect(GeneralErrorCode.INVALID_REQUEST,
                () -> CourseScheduleValidator.validateCreate(create(duration, days, places(1))));
    }

    @ParameterizedTest
    @CsvSource({"0,1", "3,1", ",1"})
    void rejectsMissingOrOutOfRangeDay(Integer day, Integer order) {
        expect(GeneralErrorCode.INVALID_REQUEST, () -> CourseScheduleValidator.validateCreate(
                create(DurationType.ONE_NIGHT, 2, List.of(item(day, order, CourseItemType.PLACE, null)))));
    }

    @ParameterizedTest
    @CsvSource(value = {"0", "2", "3", "NULL"}, nullValues = "NULL")
    void rejectsInvalidOrNonconsecutiveOrders(Integer order) {
        expect(CourseErrorCode.INVALID_COURSE_ITEM, () -> CourseScheduleValidator.validateCreate(
                create(DurationType.DAY_TRIP, 1, List.of(item(1, order, CourseItemType.PLACE, null)))));
    }

    @Test
    void rejectsDuplicateOrderWithinDay() {
        expect(CourseErrorCode.DUPLICATE_COURSE_ITEM_ORDER, () -> CourseScheduleValidator.validateCreate(
                create(DurationType.DAY_TRIP, 1, List.of(
                        item(1, 1, CourseItemType.PLACE, null), item(1, 1, CourseItemType.CONTENT, null)))));
    }

    @Test
    void rejectsMissingDayEvenWhenTotalNumberOfPlacesMatchesDays() {
        expect(CourseErrorCode.PLACE_ITEM_REQUIRED, () -> CourseScheduleValidator.validateCreate(
                create(DurationType.TWO_NIGHT, 3, List.of(
                        item(1, 1, CourseItemType.PLACE, null), item(1, 2, CourseItemType.PLACE, null),
                        item(3, 1, CourseItemType.PLACE, null)))));
    }

    @Test
    void rejectsContentOnlyDay() {
        expect(CourseErrorCode.PLACE_ITEM_REQUIRED, () -> CourseScheduleValidator.validateCreate(
                create(DurationType.ONE_NIGHT, 2, List.of(
                        item(1, 1, CourseItemType.PLACE, null), item(2, 1, CourseItemType.CONTENT, null)))));
    }

    @Test
    void rejectsTravelTimesOnFirstItemOfSecondDay() {
        expect(CourseErrorCode.INVALID_COURSE_ITEM, () -> CourseScheduleValidator.validateCreate(
                create(DurationType.ONE_NIGHT, 2, List.of(item(1, 1, CourseItemType.PLACE, null),
                        item(2, 1, CourseItemType.PLACE, List.of(new CourseReqDTO.CourseItemTimeReq(TransportMode.CAR, 10)))))));
    }

    @Test
    void acceptsMixedItemsAndTravelTimesWithinDayRegardlessOfArrayOrder() {
        assertThatCode(() -> CourseScheduleValidator.validateCreate(create(DurationType.ONE_NIGHT, 2,
                List.of(item(2, 1, CourseItemType.PLACE, List.of()),
                        item(1, 2, CourseItemType.CONTENT, List.of(new CourseReqDTO.CourseItemTimeReq(TransportMode.WALK, 10))),
                        item(1, 1, CourseItemType.PLACE, null))))).doesNotThrowAnyException();
    }

    @Test
    void preservesLegacyScheduleOnMetadataOnlyEditOrUnchangedDuration() {
        Course legacy = course(DurationType.THREE_PLUS, 4);
        assertThatCode(() -> CourseScheduleValidator.validateUpdate(legacy, update(null, null, null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> CourseScheduleValidator.validateUpdate(legacy, update(DurationType.THREE_PLUS, 4, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void requiresFullScheduleWhenDurationChanges() {
        expect(GeneralErrorCode.INVALID_REQUEST, () -> CourseScheduleValidator.validateUpdate(
                course(DurationType.DAY_TRIP, 1), update(DurationType.ONE_NIGHT, 2, null)));
        expect(GeneralErrorCode.INVALID_REQUEST, () -> CourseScheduleValidator.validateUpdate(
                course(DurationType.DAY_TRIP, 1), update(DurationType.ONE_NIGHT, null, places(2))));
    }

    @Test
    void requiresFullScheduleWhenOnlyActualDaysChange() {
        expect(GeneralErrorCode.INVALID_REQUEST, () -> CourseScheduleValidator.validateUpdate(
                course(DurationType.THREE_PLUS, 4), update(null, 5, null)));
    }

    @Test
    void validatesScheduleAgainstExistingDurationAndDaysWhenOmitted() {
        Course existing = course(DurationType.ONE_NIGHT, 2);
        assertThatCode(() -> CourseScheduleValidator.validateUpdate(existing, update(null, null, places(2))))
                .doesNotThrowAnyException();
        expect(CourseErrorCode.PLACE_ITEM_REQUIRED,
                () -> CourseScheduleValidator.validateUpdate(existing, update(null, null, places(1))));
    }

    @Test
    void acceptsDurationExpansionAndReductionWithFullSchedule() {
        assertThatCode(() -> CourseScheduleValidator.validateUpdate(course(DurationType.ONE_NIGHT, 2),
                update(DurationType.TWO_NIGHT, 3, places(3)))).doesNotThrowAnyException();
        assertThatCode(() -> CourseScheduleValidator.validateUpdate(course(DurationType.TWO_NIGHT, 3),
                update(DurationType.ONE_NIGHT, 2, places(2)))).doesNotThrowAnyException();
        assertThatCode(() -> CourseScheduleValidator.validateUpdate(course(DurationType.THREE_PLUS, 4),
                update(null, 5, places(5)))).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyReplacementSchedule() {
        expect(CourseErrorCode.PLACE_ITEM_REQUIRED, () -> CourseScheduleValidator.validateUpdate(
                course(DurationType.DAY_TRIP, 1), update(null, null, List.of())));
    }

    @Test
    void jsonOmissionsAreNotDefaultedByJavaConvenienceConstructors() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var request = mapper.readValue("""
                {"durationType":"DAY_TRIP", "courseItems":[{"order":1, "type":"PLACE"}]}
                """, CourseReqDTO.CourseCreateReq.class);
        assertThat(request.totalDays()).isNull();
        assertThat(request.courseItems().getFirst().day()).isNull();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("totalDays", "courseItems[0].day");
        }
    }

    @Test
    void swaggerRequestExamplesSatisfyDtoAndScheduleValidation() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var create = mapper.readValue(com.yeogido.backend.global.swagger.SwaggerExamples.COURSE_CREATE,
                CourseReqDTO.CourseCreateReq.class);
        var update = mapper.readValue(com.yeogido.backend.global.swagger.SwaggerExamples.COURSE_UPDATE,
                CourseReqDTO.CourseUpdateReq.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(create)).isEmpty();
            assertThat(factory.getValidator().validate(update)).isEmpty();
        }
        assertThatCode(() -> CourseScheduleValidator.validateCreate(create)).doesNotThrowAnyException();
        assertThatCode(() -> CourseScheduleValidator.validateUpdate(course(DurationType.DAY_TRIP, 1), update))
                .doesNotThrowAnyException();
    }

    private CourseReqDTO.CourseCreateReq create(DurationType duration, Integer days, List<CourseItemCreateReq> items) {
        return new CourseReqDTO.CourseCreateReq("코스", 1L, "설명", duration, TransportType.CAR,
                CompanionType.SOLO, 1, 12, "thumbnail.jpg", null, List.of(1L), items, days);
    }

    private CourseReqDTO.CourseUpdateReq update(DurationType duration, Integer days, List<CourseItemCreateReq> items) {
        return new CourseReqDTO.CourseUpdateReq("수정 제목", null, duration, null, null,
                null, null, null, null, null, items, days);
    }

    private Course course(DurationType duration, int days) {
        return Course.builder().durationType(duration).totalDays(days).build();
    }

    private List<CourseItemCreateReq> places(int days) {
        return IntStream.rangeClosed(1, days).mapToObj(day -> item(day, 1, CourseItemType.PLACE, null)).toList();
    }

    private CourseItemCreateReq item(Integer day, Integer order, CourseItemType type, List<CourseReqDTO.CourseItemTimeReq> times) {
        return new CourseItemCreateReq(order, type, type == CourseItemType.CONTENT ? 1L : null,
                null, null, null, null, null, null, null, null, null, times, day);
    }

    private void expect(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
