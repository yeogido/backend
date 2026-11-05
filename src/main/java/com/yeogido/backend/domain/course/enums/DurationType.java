package com.yeogido.backend.domain.course.enums;

public enum DurationType {
    DAY_TRIP("당일치기"),
    ONE_NIGHT("1박2일"),
    TWO_NIGHT("2박3일"),
    THREE_PLUS("3박 이상");

    private final String displayName;

    DurationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
