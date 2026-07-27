package com.yeogido.backend.global.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.yeogido.backend.global.common.response.ApiResponse;
import com.yeogido.backend.global.common.response.ValidationError;
import com.yeogido.backend.global.common.response.ValidationErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collections;
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

    @ExceptionHandler({
            NoResourceFoundException.class,
            NoHandlerFoundException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleApiNotFound(
            Exception e
    ) {

        return ResponseEntity
                .status(GeneralErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e
    ) {

        return ResponseEntity
                .status(GeneralErrorCode.INVALID_PARAMETER.getHttpStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.INVALID_PARAMETER));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e
    ) {

        return ResponseEntity
                .status(GeneralErrorCode.REQUIRED_FIELD_MISSING.getHttpStatus())
                .body(ApiResponse.onFailure(GeneralErrorCode.REQUIRED_FIELD_MISSING));
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
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e
    ) {

        if (e.getCause() instanceof InvalidFormatException ex
                && !ex.getPath().isEmpty()) {

            String field = ex.getPath().get(ex.getPath().size() - 1).getFieldName();

            ValidationError error = new ValidationError(
                    field,
                    ex.getValue(),
                    "올바르지 않은 값입니다."
            );

            return ResponseEntity
                    .status(GeneralErrorCode.INVALID_REQUEST.getHttpStatus())
                    .body(ApiResponse.<ValidationErrorResponse>builder()
                            .isSuccess(false)
                            .code(GeneralErrorCode.INVALID_REQUEST.getCode())
                            .message(GeneralErrorCode.INVALID_REQUEST.getMessage())
                            .result(new ValidationErrorResponse(Collections.singletonList(error)))
                            .build());
        }

        return ResponseEntity
                .status(GeneralErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ApiResponse.<ValidationErrorResponse>builder()
                        .isSuccess(false)
                        .code(GeneralErrorCode.INVALID_REQUEST.getCode())
                        .message(GeneralErrorCode.INVALID_REQUEST.getMessage())
                        .result(null)
                        .build());
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
