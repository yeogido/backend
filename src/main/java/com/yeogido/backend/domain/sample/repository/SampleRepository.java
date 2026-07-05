package com.yeogido.backend.domain.sample.repository;

import com.yeogido.backend.domain.sample.entity.Sample;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleRepository extends JpaRepository<Sample, Long> {
}