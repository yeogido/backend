package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.repository.CourseViewCountSyncRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseViewCountSyncRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CourseViewCountSyncTransactionService {

    private final CourseViewCountSyncRepository courseViewCountSyncRepository;
    private final CourseViewCountSyncRedisRepository courseViewCountSyncRedisRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncViewCount(
            LocalDate date,
            Long courseId,
            Long activityViewCount,
            long delta
    ) {
        if (delta > 0) {
            courseViewCountSyncRepository.increaseViewCount(courseId, delta);
        }

        courseViewCountSyncRedisRepository.updateSyncViewCount(
                date,
                courseId,
                activityViewCount
        );
    }
}