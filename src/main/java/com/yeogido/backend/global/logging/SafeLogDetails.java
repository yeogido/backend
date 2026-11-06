package com.yeogido.backend.global.logging;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.ResourceAccessException;

public final class SafeLogDetails {

    private static final int MAX_CAUSE_DEPTH = 20;
    private static final int MAX_STACK_FRAMES = 20;

    private SafeLogDetails() { }

    public static String failureType(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean connectionFailure = false;
        for (Throwable cause = failure; cause != null && visited.size() < MAX_CAUSE_DEPTH
                && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return "timeout";
            }
            if (cause instanceof HttpMessageConversionException) {
                return "response_parse_error";
            }
            connectionFailure |= cause instanceof ResourceAccessException;
        }
        return connectionFailure ? "connection_error" : "client_error";
    }

    // 메시지, 원인, 억제 예외를 제외한 코드 호출 위치만 기록
    public static String stackFrames(Throwable failure) {
        return Arrays.stream(failure.getStackTrace())
                .limit(MAX_STACK_FRAMES)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining(" <- "));
    }
}
