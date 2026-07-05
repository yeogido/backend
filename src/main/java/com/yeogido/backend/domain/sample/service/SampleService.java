package com.yeogido.backend.domain.sample.service;

import com.yeogido.backend.domain.sample.dto.response.SampleResponse;

public interface SampleService {

    SampleResponse.Detail getSample(Long sampleId);

}