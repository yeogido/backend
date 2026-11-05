package com.yeogido.backend.domain.course.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseSemanticSearchSessionRepository {

    private static final String PREFIX = "course:semantic-search:";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public CourseSemanticSearchSessionRepository(StringRedisTemplate redis, ObjectMapper mapper,
            @Value("${app.course-search.embedding.session-ttl-seconds:900}") long ttlSeconds) {
        if (ttlSeconds < 1 || ttlSeconds > 86400) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
        }
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public String save(String query, List<Long> courseIds) {
        String id = UUID.randomUUID().toString();
        try {
            redis.opsForValue().set(PREFIX + id,
                    mapper.writeValueAsString(new Snapshot(query, List.copyOf(courseIds))), ttl);
            return id;
        } catch (Exception exception) {
            throw new GeneralException(CourseErrorCode.COURSE_SEARCH_API_ERROR);
        }
    }

    public List<Long> find(String id, String query) {
        String json;
        try {
            json = redis.opsForValue().get(PREFIX + id);
        } catch (Exception exception) {
            throw new GeneralException(CourseErrorCode.COURSE_SEARCH_API_ERROR);
        }
        if (json == null) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CURSOR);
        }
        try {
            Snapshot snapshot = mapper.readValue(json, Snapshot.class);
            if (!query.equals(snapshot.query()) || snapshot.courseIds() == null
                    || snapshot.courseIds().stream().anyMatch(courseId -> courseId == null || courseId < 1)) {
                throw new IllegalArgumentException();
            }
            return List.copyOf(snapshot.courseIds());
        } catch (Exception exception) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CURSOR);
        }
    }

    private record Snapshot(String query, List<Long> courseIds) { }
}
