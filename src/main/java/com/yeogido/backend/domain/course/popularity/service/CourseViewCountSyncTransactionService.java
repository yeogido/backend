package com.yeogido.backend.domain.course.popularity.service;

import com.yeogido.backend.domain.course.popularity.repository.CourseViewCountSyncRedisRepository;
import com.yeogido.backend.domain.course.popularity.repository.CourseViewCountSyncRepository;
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

        // 코스별 독립 트랜잭션
        // 다음 동기화를 위한 기준값 갱신
        courseViewCountSyncRedisRepository.updateSyncViewCount(
                date,
                courseId,
                activityViewCount
        );
    }
}
