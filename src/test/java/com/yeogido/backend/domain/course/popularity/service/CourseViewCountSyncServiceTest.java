package com.yeogido.backend.domain.course.popularity.service;

import com.yeogido.backend.domain.course.popularity.repository.CourseViewCountSyncRedisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseViewCountSyncServiceTest {

    @Mock
    private CourseViewCountSyncRedisRepository courseViewCountSyncRedisRepository;

    @Mock
    private CourseViewCountSyncTransactionService transactionService;

    @InjectMocks
    private CourseViewCountSyncService courseViewCountSyncService;

    @Test
    void syncRecentViewCountsProcessesSevenDays() {
        LocalDate today = LocalDate.now();

        when(courseViewCountSyncRedisRepository.getActivityViewCounts(any()))
                .thenReturn(Map.of());

        courseViewCountSyncService.syncRecentViewCounts();

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);

        verify(courseViewCountSyncRedisRepository, times(7))
                .getActivityViewCounts(dateCaptor.capture());

        List<LocalDate> dates = dateCaptor.getAllValues();

        assertThat(dates).containsExactly(
                today,
                today.minusDays(1),
                today.minusDays(2),
                today.minusDays(3),
                today.minusDays(4),
                today.minusDays(5),
                today.minusDays(6)
        );
    }

    @Test
    void syncRecentViewCountsContinuesWhenOneDateFails() {
        LocalDate today = LocalDate.now();

        when(courseViewCountSyncRedisRepository.getActivityViewCounts(today))
                .thenReturn(Map.of(1L, 4L));
        when(courseViewCountSyncRedisRepository.getSyncViewCounts(today))
                .thenReturn(Map.of());

        when(courseViewCountSyncRedisRepository.getActivityViewCounts(today.minusDays(1)))
                .thenThrow(new RuntimeException("redis down"));

        when(courseViewCountSyncRedisRepository.getActivityViewCounts(today.minusDays(2)))
                .thenReturn(Map.of(2L, 9L));
        when(courseViewCountSyncRedisRepository.getSyncViewCounts(today.minusDays(2)))
                .thenReturn(Map.of());

        courseViewCountSyncService.syncRecentViewCounts();

        verify(transactionService)
                .syncViewCount(today, 1L, 4L, 4L);

        verify(transactionService)
                .syncViewCount(today.minusDays(2), 2L, 9L, 9L);
    }

    @Test
    void syncRecentViewCountsContinuesWhenOneCourseFails() {
        LocalDate today = LocalDate.now();

        when(courseViewCountSyncRedisRepository.getActivityViewCounts(today))
                .thenReturn(Map.of(1L, 10L, 2L, 5L));
        when(courseViewCountSyncRedisRepository.getSyncViewCounts(today))
                .thenReturn(Map.of(1L, 7L, 2L, 5L));

        doThrow(new RuntimeException("db error"))
                .when(transactionService)
                .syncViewCount(eq(today), eq(1L), eq(10L), eq(3L));

        courseViewCountSyncService.syncRecentViewCounts();

        verify(transactionService)
                .syncViewCount(today, 1L, 10L, 3L);

        verify(transactionService)
                .syncViewCount(today, 2L, 5L, 0L);
    }
}