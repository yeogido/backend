package com.yeogido.backend.domain.course.enums;

public enum CourseSortType {
    RECOMMEND,
    DISTANCE,
    LATEST,
    SAVED,
    REVIEW,
    POPULAR;

    public static CourseSortType resolve(CourseSortType sort) {
        return sort == null
                ? LATEST
                : sort;
    }
}
