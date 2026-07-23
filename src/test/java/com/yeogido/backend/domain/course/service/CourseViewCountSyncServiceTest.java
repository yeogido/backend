package com.yeogido.backend.domain.course.service;

import com.yeogido.backend.domain.course.repository.CourseViewCountSyncRedisRepository;
import com.yeogido.backend.domain.course.repository.CourseViewCountSyncRepository;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseViewCountSyncServiceTest {

    @Mock
    private CourseViewCountSyncRedisRepository courseViewCountSyncRedisRepository;

    @Mock
    private CourseViewCountSyncRepository courseViewCountSyncRepository;

    @InjectMocks
    private CourseViewCountSyncService courseViewCountSyncService;

    @Test
    void syncViewCountsAppliesDeltaAndUpdatesSyncKey() {
        LocalDate date = LocalDate.of(2026, 7, 23);

        when(courseViewCountSyncRedisRepository.getActivityViewCounts(date))
                .thenReturn(Map.of(1L, 10L, 2L, 5L));
        when(courseViewCountSyncRedisRepository.getSyncViewCounts(date))
                .thenReturn(Map.of(1L, 7L, 2L, 5L));

        courseViewCountSyncService.syncViewCounts(date);

        verify(courseViewCountSyncRepository).increaseViewCount(1L, 3L);
        verify(courseViewCountSyncRedisRepository).updateSyncViewCount(date, 1L, 10L);
        verify(courseViewCountSyncRedisRepository).updateSyncViewCount(date, 2L, 5L);
    }

    @Test
    void syncRecentViewCountsContinuesWhenOneDateFails() {
        LocalDate today = LocalDate.now();

        when(courseViewCountSyncRedisRepository.getActivityViewCounts(today))
                .thenReturn(Map.of(1L, 4L));
        when(courseViewCountSyncRedisRepository.getSyncViewCounts(today))
                .thenReturn(Map.of());
        when(courseViewCountSyncRedisRepository.getActivityViewCounts(today.minusDays(2)))
                .thenReturn(Map.of(2L, 9L));
        when(courseViewCountSyncRedisRepository.getSyncViewCounts(today.minusDays(2)))
                .thenReturn(Map.of());
        when(courseViewCountSyncRedisRepository.getActivityViewCounts(today.minusDays(1)))
                .thenThrow(new RuntimeException("redis down"));

        courseViewCountSyncService.syncRecentViewCounts();

        verify(courseViewCountSyncRepository).increaseViewCount(1L, 4L);
        verify(courseViewCountSyncRedisRepository).updateSyncViewCount(today, 1L, 4L);
        verify(courseViewCountSyncRepository).increaseViewCount(2L, 9L);
        verify(courseViewCountSyncRedisRepository).updateSyncViewCount(today.minusDays(2), 2L, 9L);
    }

    @Test
    void syncRecentViewCountsProcessesSevenDays() {
        LocalDate today = LocalDate.now();
        when(courseViewCountSyncRedisRepository.getActivityViewCounts(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());

        courseViewCountSyncService.syncRecentViewCounts();

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(courseViewCountSyncRedisRepository, org.mockito.Mockito.times(7))
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
}
