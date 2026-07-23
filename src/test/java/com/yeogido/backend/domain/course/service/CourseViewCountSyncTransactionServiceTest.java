package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.repository.CourseViewCountSyncRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseViewCountSyncRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseViewCountSyncTransactionServiceTest {

    @Mock
    private CourseViewCountSyncRepository courseViewCountSyncRepository;

    @Mock
    private CourseViewCountSyncRedisRepository courseViewCountSyncRedisRepository;

    @InjectMocks
    private CourseViewCountSyncTransactionService courseViewCountSyncTransactionService;

    @Test
    void syncViewCountIncreasesViewCountAndUpdatesSyncKey() {
        LocalDate date = LocalDate.of(2026, 7, 23);

        courseViewCountSyncTransactionService.syncViewCount(
                date,
                1L,
                10L,
                3L
        );

        InOrder inOrder = inOrder(
                courseViewCountSyncRepository,
                courseViewCountSyncRedisRepository
        );

        inOrder.verify(courseViewCountSyncRepository)
                .increaseViewCount(1L, 3L);

        inOrder.verify(courseViewCountSyncRedisRepository)
                .updateSyncViewCount(date, 1L, 10L);
    }

    @Test
    void syncViewCountUpdatesOnlySyncKeyWhenDeltaIsZero() {
        LocalDate date = LocalDate.of(2026, 7, 23);

        courseViewCountSyncTransactionService.syncViewCount(
                date,
                1L,
                10L,
                0L
        );

        verify(courseViewCountSyncRepository, never())
                .increaseViewCount(1L, 0L);

        verify(courseViewCountSyncRedisRepository)
                .updateSyncViewCount(date, 1L, 10L);
    }

    @Test
    void syncViewCountUpdatesOnlySyncKeyWhenDeltaIsNegative() {
        LocalDate date = LocalDate.of(2026, 7, 23);

        courseViewCountSyncTransactionService.syncViewCount(
                date,
                1L,
                8L,
                -2L
        );

        verify(courseViewCountSyncRepository, never())
                .increaseViewCount(1L, -2L);

        verify(courseViewCountSyncRedisRepository)
                .updateSyncViewCount(date, 1L, 8L);
    }
}