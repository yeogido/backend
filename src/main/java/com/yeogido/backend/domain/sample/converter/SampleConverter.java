package com.yeogido.backend.domain.sample.converter;

import com.yeogido.backend.domain.sample.dto.request.SampleRequest;
import com.yeogido.backend.domain.sample.dto.response.SampleResponse;
import com.yeogido.backend.domain.sample.entity.Sample;

public class SampleConverter {

    private SampleConverter() {
    }

    public static Sample toEntity(SampleRequest.Create request) {
        return Sample.builder()
                .message(request.message())
                .build();
    }

    public static SampleResponse.Detail toDetail(Sample sample) {
        return new SampleResponse.Detail(
                sample.getId(),
                sample.getMessage()
        );
    }
}