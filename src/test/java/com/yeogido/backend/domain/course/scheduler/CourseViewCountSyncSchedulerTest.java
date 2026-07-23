package com.yeogido.backend.domain.course.scheduler;

import com.yeogido.backend.domain.course.service.CourseViewCountSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseViewCountSyncSchedulerTest {

    @Mock
    private CourseViewCountSyncService courseViewCountSyncService;

    @InjectMocks
    private CourseViewCountSyncScheduler courseViewCountSyncScheduler;

    @Test
    void syncCourseViewCountsDelegatesToService() {
        courseViewCountSyncScheduler.syncCourseViewCounts();

        verify(courseViewCountSyncService).syncRecentViewCounts();
    }
}
