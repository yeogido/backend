package com.yeogido.backend.global.logging;

import com.yeogido.backend.global.exception.GeneralException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.util.ErrorHandler;

public final class SafeSchedulerErrorHandler implements ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(SafeSchedulerErrorHandler.class);

    @Override
    public void handleError(Throwable failure) {
        try {
            LoggingEventBuilder event = log.atError()
                    .addKeyValue("event", "scheduler.task.failed")
                    .addKeyValue("exceptionType", failure.getClass().getName())
                    .addKeyValue("stackFrames", SafeLogDetails.stackFrames(failure));
            if (failure instanceof GeneralException generalException) {
                event.addKeyValue("errorCode", generalException.getErrorCode().getCode());
            }
            event.log("스케줄러 작업 경계 오류");
        } catch (RuntimeException ignored) {
            // 로그 기록 실패에 의한 반복 작업 중단 방지
        }
    }
}
