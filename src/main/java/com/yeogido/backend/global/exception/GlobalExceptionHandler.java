package com.yeogido.backend.global.exception;

import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.ValidationError;
import com.yeogido.backend.global.common.response.ValidationErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(
            GeneralException e
    ) {

        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.onFailure(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleValidationException(
            MethodArgumentNotValidException e
    ) {

        List<ValidationError> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ValidationError(
                        error.getField(),
                        getRejectedValue(error),
                        error.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity
                .status(GeneralErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ApiResponse.<ValidationErrorResponse>builder()
                        .isSuccess(false)
                        .code(GeneralErrorCode.INVALID_REQUEST.getCode())
                        .message(GeneralErrorCode.INVALID_REQUEST.getMessage())
                        .result(new ValidationErrorResponse(errors))
                        .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e
    ) {

        return ResponseEntity
                .status(GeneralErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.INVALID_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception e
    ) {

        log.error("Unexpected exception occurred", e);

        return ResponseEntity
                .status(GeneralErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.onFailure(
                        GeneralErrorCode.INTERNAL_SERVER_ERROR
                ));
    }

    private Object getRejectedValue(FieldError error) {

        String field = error.getField().toLowerCase();

        if (field.contains("password")
                || field.contains("accesstoken")
                || field.contains("refreshtoken")) {
            return "********";
        }

        return error.getRejectedValue();
    }
}