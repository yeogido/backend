package com.yeogido.backend.domain.sample.service;

import com.yeogido.backend.domain.sample.converter.SampleConverter;
import com.yeogido.backend.domain.sample.dto.response.SampleResponse;
import com.yeogido.backend.domain.sample.entity.Sample;
import com.yeogido.backend.domain.sample.exception.SampleErrorCode;
import com.yeogido.backend.domain.sample.exception.SampleException;
import com.yeogido.backend.domain.sample.repository.SampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SampleServiceImpl implements SampleService {

    private final SampleRepository sampleRepository;

    @Override
    @Transactional(readOnly = true)
    public SampleResponse.Detail getSample(Long sampleId) {

        Sample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new SampleException(
                        SampleErrorCode.SAMPLE_NOT_FOUND
                ));

        return SampleConverter.toDetail(sample);
    }
}