package com.yeogido.backend.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Year;

public class BirthYearValidator implements ConstraintValidator<ValidBirthYear, String> {

    private static final int MIN_AGE = 10;
    private static final String YEAR_PATTERN = "^\\d{4}$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (!value.matches(YEAR_PATTERN)) {
            return false;
        }

        int birthYear = Integer.parseInt(value);
        int currentYear = Year.now().getValue();

        return birthYear <= currentYear - MIN_AGE;
    }
}
