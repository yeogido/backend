package com.yeogido.backend.domain.business.dto.request;

import com.yeogido.backend.domain.business.enums.PromotionCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 홍보 등록·수정 요청 DTO의 길이·형식 검증.
 * 컨트롤러의 {@code @Valid}가 서비스 진입 전(이미지 이동 전)에 COMMON4001로 거절하는 규칙이다.
 */
class BusinessPromotionRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ---------- 길이 ----------

    @Test
    void register_WhenShortDescriptionIsAtMaxLength_HasNoViolation() {
        BusinessPromotionRequest.Register request =
                validRegister().shortDescription("가".repeat(255)).build();

        assertThat(violatedFields(request)).isEmpty();
    }

    @Test
    void register_WhenShortDescriptionExceedsMaxLength_HasViolation() {
        BusinessPromotionRequest.Register request =
                validRegister().shortDescription("가".repeat(256)).build();

        assertThat(violatedFields(request)).containsExactly("shortDescription");
    }

    @Test
    void register_WhenPhoneNumberIsAtMaxLength_HasNoViolation() {
        // 20자: 숫자 16개 + 하이픈 4개
        BusinessPromotionRequest.Register request =
                validRegister().phoneNumber("1234-5678-1234-5678-").build();

        assertThat(violatedFields(request)).isEmpty();
    }

    @Test
    void register_WhenPhoneNumberExceedsMaxLength_HasViolation() {
        BusinessPromotionRequest.Register request =
                validRegister().phoneNumber("1234-5678-1234-5678-1").build();

        assertThat(violatedFields(request)).containsExactly("phoneNumber");
    }

    @Test
    void register_WhenSnsAccountIsAtMaxLength_HasNoViolation() {
        BusinessPromotionRequest.Register request =
                validRegister().snsAccount("https://" + "a".repeat(92)).build();

        assertThat(violatedFields(request)).isEmpty();
    }

    @Test
    void register_WhenSnsAccountExceedsMaxLength_HasViolation() {
        BusinessPromotionRequest.Register request =
                validRegister().snsAccount("https://" + "a".repeat(93)).build();

        assertThat(violatedFields(request)).containsExactly("snsAccount");
    }

    // ---------- 전화번호 형식 ----------

    @ParameterizedTest
    @ValueSource(strings = {"010-1234-5678", "02 123 4567", "+82-10-1234-5678", "0212345678"})
    void register_WhenPhoneNumberIsValid_HasNoViolation(String phoneNumber) {
        BusinessPromotionRequest.Register request =
                validRegister().phoneNumber(phoneNumber).build();

        assertThat(violatedFields(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "123", "010-12a4-5678", "010.1234.5678", "1234567"})
    void register_WhenPhoneNumberIsInvalid_HasViolation(String phoneNumber) {
        BusinessPromotionRequest.Register request =
                validRegister().phoneNumber(phoneNumber).build();

        assertThat(violatedFields(request)).containsExactly("phoneNumber");
    }

    // ---------- SNS 형식 ----------

    @ParameterizedTest
    @ValueSource(strings = {"https://instagram.com/abc", "http://blog.naver.com/abc"})
    void register_WhenSnsAccountIsValid_HasNoViolation(String snsAccount) {
        BusinessPromotionRequest.Register request =
                validRegister().snsAccount(snsAccount).build();

        assertThat(violatedFields(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"instagram.com/abc", "@abc", "javascript:alert(1)", "https://insta gram.com"})
    void register_WhenSnsAccountIsInvalid_HasViolation(String snsAccount) {
        BusinessPromotionRequest.Register request =
                validRegister().snsAccount(snsAccount).build();

        assertThat(violatedFields(request)).containsExactly("snsAccount");
    }

    @Test
    void register_WhenSnsAccountIsNull_HasNoViolation() {
        BusinessPromotionRequest.Register request =
                validRegister().snsAccount(null).build();

        assertThat(violatedFields(request)).isEmpty();
    }

    // ---------- 수정 요청 ----------

    @Test
    void update_WhenAllFieldsAreNull_HasNoViolation() {
        // 보내지 않은 필드는 검사하지 않는다 (필수 여부는 서비스에서 판단).
        BusinessPromotionRequest.Update request =
                BusinessPromotionRequest.Update.builder().build();

        assertThat(violatedFields(request)).isEmpty();
    }

    @Test
    void update_WhenValuesAreAtMaxLength_HasNoViolation() {
        BusinessPromotionRequest.Update request =
                BusinessPromotionRequest.Update.builder()
                        .shortDescription("가".repeat(255))
                        .phoneNumber("1234-5678-1234-5678-")
                        .snsAccount("https://" + "a".repeat(92))
                        .build();

        assertThat(violatedFields(request)).isEmpty();
    }

    @Test
    void update_WhenValuesExceedMaxLength_HasViolationPerField() {
        BusinessPromotionRequest.Update request =
                BusinessPromotionRequest.Update.builder()
                        .shortDescription("가".repeat(256))
                        .phoneNumber("1234-5678-1234-5678-1")
                        .snsAccount("https://" + "a".repeat(93))
                        .build();

        assertThat(violatedFields(request))
                .containsExactlyInAnyOrder("shortDescription", "phoneNumber", "snsAccount");
    }

    @Test
    void update_WhenPhoneNumberAndSnsAccountHaveInvalidFormat_HasViolation() {
        BusinessPromotionRequest.Update request =
                BusinessPromotionRequest.Update.builder()
                        .phoneNumber("abc")
                        .snsAccount("@abc")
                        .build();

        assertThat(violatedFields(request))
                .containsExactlyInAnyOrder("phoneNumber", "snsAccount");
    }

    // ---------- helpers ----------

    private static BusinessPromotionRequest.Register.RegisterBuilder validRegister() {
        return BusinessPromotionRequest.Register.builder()
                .businessInfoId(1L)
                .shortDescription("바다 뷰 카페")
                .businessHours(List.of(
                        new BusinessPromotionRequest.BusinessHour(
                                "MONDAY", LocalTime.of(10, 0), LocalTime.of(22, 0)
                        )
                ))
                .snsAccount("https://instagram.com/waveoncoffee")
                .phoneNumber("051-727-1660")
                .promotionCategory(PromotionCategory.CAFE)
                .images(List.of(
                        new BusinessPromotionRequest.Image("temp/image.jpg", 1)
                ));
    }

    private static <T> Set<String> violatedFields(T request) {
        return violations(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static <T> Set<ConstraintViolation<T>> violations(T request) {
        return validator.validate(request);
    }
}
