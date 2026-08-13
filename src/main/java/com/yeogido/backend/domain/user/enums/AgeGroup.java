package com.yeogido.backend.domain.user.enums;

import java.time.Year;

public enum AgeGroup {
    TEENS,
    TWENTIES,
    THIRTIES,
    FORTIES,
    FIFTIES,
    SIXTIES,
    SEVENTIES,
    EIGHTIES,
    NINETIES,
    HUNDRED_PLUS;

    public static AgeGroup fromBirthYear(String birthYear) {
        if (birthYear == null || birthYear.isBlank()) {
            return null;
        }

        try {
            int age = Year.now().getValue() - Integer.parseInt(birthYear) + 1;
            return fromAge(age);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static AgeGroup fromAge(int age) {
        if (age <= 0) {
            return null;
        }

        if (age < 20) {
            return TEENS;
        }

        if (age < 30) {
            return TWENTIES;
        }

        if (age < 40) {
            return THIRTIES;
        }

        if (age < 50) {
            return FORTIES;
        }

        if (age < 60) {
            return FIFTIES;
        }

        if (age < 70) {
            return SIXTIES;
        }

        if (age < 80) {
            return SEVENTIES;
        }

        if (age < 90) {
            return EIGHTIES;
        }

        if (age < 100) {
            return NINETIES;
        }

        return HUNDRED_PLUS;
    }
}
