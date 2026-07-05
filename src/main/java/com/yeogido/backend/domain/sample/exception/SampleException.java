package com.yeogido.backend.domain.sample.exception;

import com.yeogido.backend.global.exception.GeneralException;

public class SampleException extends GeneralException {

    public SampleException(SampleErrorCode errorCode) {
        super(errorCode);
    }

}

