package com.yeogido.backend.domain.course.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.FilterKeywords;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.Interpretation;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CourseRelatedSearchSessionRepositoryTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final CourseRelatedSearchSessionRepository sessions =
            new CourseRelatedSearchSessionRepository(redis, new ObjectMapper(), 3600);

    @Test
    void storesSearchContextForOneHour() {
        var original = new CourseListReq(null, "부산 데이트", null, null, null, null, null,
                null, null, null, null, null, 20);
        var effective = new CourseListReq(null, null, null, 42L, null, null, null,
                null, null, null, null, null, 20);
        var interpretation = new Interpretation(effective, List.of("데이트"), null,
                List.of("부산", "데이트"), null, new FilterKeywords("부산", null, null, null));
        var session = new CourseRelatedSearchSessionRepository.Session(1L, original, interpretation,
                new CourseQueryRepository.SearchMembership(List.of(10L), List.of(11L)));
        when(redis.opsForValue()).thenReturn(values);

        UUID searchId = sessions.save(session);

        String key = "course:related-search:" + searchId;
        var json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq(key), json.capture(), eq(Duration.ofHours(1)));
        when(values.get(key)).thenReturn(json.getValue());
        assertThat(sessions.find(searchId)).isEqualTo(session);
    }

    @Test
    void missingSearchIdReturnsSessionExpired() {
        when(redis.opsForValue()).thenReturn(values);
        var searchId = UUID.randomUUID();

        assertThatThrownBy(() -> sessions.find(searchId))
                .isInstanceOfSatisfying(GeneralException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CourseErrorCode.COURSE_SEARCH_SESSION_EXPIRED));
    }
}
