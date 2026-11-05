package com.yeogido.backend.domain.course.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CourseSemanticSearchSessionRepositoryTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final CourseSemanticSearchSessionRepository sessions =
            new CourseSemanticSearchSessionRepository(redis, new ObjectMapper(), 900);

    @Test
    void storesAnImmutableCandidateSnapshotWithTtlIncludingEmptyResults() {
        when(redis.opsForValue()).thenReturn(values);
        String id = sessions.save("한적한", List.of());
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("course:semantic-search:" + id), json.capture(), eq(Duration.ofMinutes(15)));
        when(values.get("course:semantic-search:" + id)).thenReturn(json.getValue());
        assertThat(sessions.find(id, "한적한")).isEmpty();
    }

    @Test
    void expiredCorruptOrOtherQuerySnapshotsAreRejectedInsteadOfRecomputed() {
        when(redis.opsForValue()).thenReturn(values);
        for (String json : new String[] {null, "bad", "{\"query\":\"다른 말\",\"courseIds\":[1]}",
                "{\"query\":\"비 오는 날\",\"courseIds\":[-1]}"}) {
            when(values.get("course:semantic-search:id")).thenReturn(json);
            assertThatThrownBy(() -> sessions.find("id", "비 오는 날"))
                    .isInstanceOf(GeneralException.class);
        }
    }

    @Test
    void redisFailureIsAnErrorAndDoesNotReturnAnUnfilteredCandidateList() {
        when(redis.opsForValue()).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> sessions.save("데이트", List.of(1L))).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> sessions.find("id", "데이트")).isInstanceOf(GeneralException.class);
    }
}
