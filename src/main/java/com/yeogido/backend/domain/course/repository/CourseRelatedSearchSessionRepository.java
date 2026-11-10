package com.yeogido.backend.domain.course.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.course.dto.CourseSearchDTO.Interpretation;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseRelatedSearchSessionRepository {

    private static final String PREFIX = "course:related-search:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public CourseRelatedSearchSessionRepository(StringRedisTemplate redis, ObjectMapper mapper,
            @Value("${app.course-search.embedding.session-ttl-seconds:3600}") long ttlSeconds) {
        if (ttlSeconds < 1 || ttlSeconds > 86400) {
            throw new GeneralException(CourseErrorCode.INVALID_COURSE_SEARCH_CONFIG);
        }
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public UUID save(Session session) {
        UUID id = UUID.randomUUID();
        try {
            redis.opsForValue().set(PREFIX + id, mapper.writeValueAsString(session), ttl);
            return id;
        } catch (JsonProcessingException | DataAccessException exception) {
            throw new GeneralException(CourseErrorCode.COURSE_SEARCH_API_ERROR);
        }
    }

    public Session find(UUID id) {
        try {
            String json = redis.opsForValue().get(PREFIX + id);
            if (json == null) {
                throw new GeneralException(CourseErrorCode.COURSE_SEARCH_SESSION_EXPIRED);
            }
            return mapper.readValue(json, Session.class);
        } catch (JsonProcessingException | DataAccessException exception) {
            throw new GeneralException(CourseErrorCode.COURSE_SEARCH_API_ERROR);
        }
    }

    public record Session(Long userId, CourseListReq original, Interpretation interpretation,
                          List<Long> semanticCandidateIds) { }
}
