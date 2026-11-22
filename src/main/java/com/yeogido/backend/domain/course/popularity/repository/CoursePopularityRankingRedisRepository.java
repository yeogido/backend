package com.yeogido.backend.domain.course.popularity.repository;

import com.yeogido.backend.domain.course.popularity.dto.CoursePopularityRankingEntry;
import com.yeogido.backend.domain.course.enums.CourseType;
import com.yeogido.backend.global.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CoursePopularityRankingRedisRepository {

    private static final String DOMAIN = "course";
    private static final String RANKING = "ranking";
    private static final String OFFICIAL = "official";
    private static final String LOCAL = "local";
    private static final String TMP = "tmp";
    private static final String LIST_SENTINEL = "!";
    private static final Comparator<RankedCourse> LIST_ORDER = Comparator.comparingLong(RankedCourse::score)
            .thenComparingLong(RankedCourse::courseId).reversed();
    private static final DefaultRedisScript<List> LIST_PAGE = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local start, stop
            if ARGV[1] == 'positive' then
                start = 0
                stop = redis.call('ZCOUNT', key, '(0', '+inf')
            else
                start = redis.call('ZCOUNT', key, '0', '+inf')
                stop = redis.call('ZCARD', key)
            end

            if ARGV[2] ~= '' and start < stop then
                local score = tonumber(ARGV[2])
                local member = ARGV[3]
                local storedScore = redis.call('ZSCORE', key, member)
                if storedScore and tonumber(storedScore) == score then
                    start = math.max(start, redis.call('ZREVRANK', key, member) + 1)
                else
                    -- The cursor can refer to a course that changed score or disappeared after a refresh.
                    local upper = stop
                    while start < upper do
                        local middle = math.floor((start + upper) / 2)
                        local entry = redis.call('ZREVRANGE', key, middle, middle, 'WITHSCORES')
                        local entryScore = tonumber(entry[2])
                        if entryScore > score or (entryScore == score and entry[1] >= member) then
                            start = middle + 1
                        else
                            upper = middle
                        end
                    end
                end
            end
            if start >= stop then
                return {}
            end
            return redis.call('ZREVRANGE', key, start, math.min(stop, start + tonumber(ARGV[4])) - 1, 'WITHSCORES')
            """, List.class);

    private final StringRedisTemplate stringRedisTemplate;

    public void replaceOfficialRanking(List<CoursePopularityRankingEntry> entries) {
        replaceRanking(officialRankingKey(), entries);
    }

    public Map<Long, Long> findOfficialCourseScores() {
        return findCourseScores(officialRankingKey());
    }

    public void replaceLocalRanking(List<CoursePopularityRankingEntry> entries) {
        replaceRanking(localRankingKey(), entries);
    }

    public List<Long> findTopLocalCourseIds(int size) {
        return findTopCourseIds(localRankingKey(), size);
    }

    public Map<Long, Long> findLocalCourseScores() {
        return findCourseScores(localRankingKey());
    }

    public Map<Long, Long> findCourseScores(CourseType courseType) {
        return courseType == CourseType.LOCAL
                ? findLocalCourseScores()
                : findOfficialCourseScores();
    }

    public void removeLocalCourseId(Long courseId) {
        removeCourseId(localRankingKey(), courseId);
        stringRedisTemplate.opsForZSet().remove(listRankingKey(localRankingKey()), listMember(courseId));
    }

    public boolean hasListRanking(CourseType type) {
        return rankingKeys(type).stream().allMatch(key ->
                Boolean.TRUE.equals(stringRedisTemplate.hasKey(listRankingKey(key)))
                        || Boolean.FALSE.equals(stringRedisTemplate.hasKey(key)));
    }

    public List<RankedCourse> findListRankingPage(
            CourseType type, Long cursorScore, Long cursorId, boolean positive, int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }
        return rankingKeys(type).stream()
                .flatMap(key -> listPage(key, cursorScore, cursorId, positive, limit).stream())
                .sorted(LIST_ORDER)
                .limit(limit)
                .toList();
    }

    public Set<Long> findNonZeroRankedCourseIds(CourseType type, List<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        Object[] members = ids.stream().map(this::listMember).toArray();
        Set<Long> ranked = new HashSet<>();
        for (String key : rankingKeys(type)) {
            List<Double> scores = stringRedisTemplate.opsForZSet().score(listRankingKey(key), members);
            if (scores != null) {
                for (int i = 0; i < scores.size(); i++) {
                    Double score = scores.get(i);
                    if (score != null && score.longValue() != 0) {
                        ranked.add(ids.get(i));
                    }
                }
            }
        }
        return ranked;
    }

    private List<RankedCourse> listPage(
            String key, Long cursorScore, Long cursorId, boolean positive, int limit
    ) {
        List<?> values = stringRedisTemplate.execute(LIST_PAGE, List.of(listRankingKey(key)),
                positive ? "positive" : "negative", cursorScore == null ? "" : cursorScore.toString(),
                cursorId == null ? "" : listMember(cursorId), Integer.toString(limit));
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<RankedCourse> courses = new ArrayList<>(values.size() / 2);
        for (int i = 0; i < values.size(); i += 2) {
            courses.add(new RankedCourse(Long.parseLong((String) values.get(i)),
                    Double.valueOf((String) values.get(i + 1)).longValue()));
        }
        return courses;
    }

    private List<String> rankingKeys(CourseType type) {
        if (type == null) {
            return List.of(officialRankingKey(), localRankingKey());
        }
        return List.of(type == CourseType.LOCAL ? localRankingKey() : officialRankingKey());
    }

    public record RankedCourse(long courseId, long score) {
    }

    private void replaceRanking(String key, List<CoursePopularityRankingEntry> entries) {
        String listKey = listRankingKey(key);
        String tmpKey = temporaryRankingKey(key);
        String listTmpKey = temporaryRankingKey(listKey) + ":" + UUID.randomUUID();

        // 이전 실패로 남아있을 수 있는 임시 Key만 초기화한다.
        if (!entries.isEmpty()) {
            stringRedisTemplate.delete(tmpKey);
        }

        // ZSet score는 인기 점수 그대로 유지하고,
        // member에 createdAt을 고정폭으로 포함해 동점 시 최신 코스가 먼저 오도록 한다.
        // Pipeline으로 임시 Key에 Redis 요청을 일괄 처리한다.
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] redisKey = serialize(tmpKey);
            byte[] listRedisKey = serialize(listTmpKey);

            for (CoursePopularityRankingEntry entry : entries) {
                connection.zAdd(
                        redisKey,
                        entry.score(),
                        serialize(redisMember(entry))
                );
                connection.zAdd(listRedisKey, entry.score(), serialize(listMember(entry.courseId())));
            }
            connection.zAdd(listRedisKey, 0, serialize(LIST_SENTINEL));
            connection.expire(listRedisKey, 3600);

            return null;
        });

        stringRedisTemplate.rename(listTmpKey, listKey);
        stringRedisTemplate.persist(listKey);
        if (entries.isEmpty()) {
            stringRedisTemplate.delete(key);
        } else {
            stringRedisTemplate.rename(tmpKey, key);
        }
    }

    private List<Long> findTopCourseIds(String key, int size) {
        if (size <= 0) {
            return List.of();
        }

        // score 기준 인기순 조회
        Set<String> courseIds = stringRedisTemplate.opsForZSet()
                .reverseRange(key, 0, size - 1L);

        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }

        return courseIds.stream()
                .map(this::courseIdFromRedisMember)
                .map(Long::valueOf)
                .toList();
    }

    private Map<Long, Long> findCourseScores(String key) {
        Set<ZSetOperations.TypedTuple<String>> entries = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, -1);

        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> scores = new LinkedHashMap<>();
        for (ZSetOperations.TypedTuple<String> entry : entries) {
            String member = entry.getValue();
            Double score = entry.getScore();

            if (member == null || score == null) {
                continue;
            }

            scores.put(Long.valueOf(courseIdFromRedisMember(member)), score.longValue());
        }

        return scores;
    }

    private void removeCourseId(String key, Long courseId) {
        Set<String> members = stringRedisTemplate.opsForZSet()
                .range(key, 0, -1);

        if (members == null || members.isEmpty()) {
            return;
        }

        String courseIdValue = courseId.toString();
        Object[] removableMembers = members.stream()
                .filter(member -> courseIdValue.equals(courseIdFromRedisMember(member)))
                .toArray();

        if (removableMembers.length == 0) {
            return;
        }

        stringRedisTemplate.opsForZSet()
                .remove(key, removableMembers);
    }

    private String redisMember(CoursePopularityRankingEntry entry) {
        LocalDateTime createdAt = Objects.requireNonNull(entry.createdAt());
        long createdAtMicros = createdAt.toEpochSecond(ZoneOffset.UTC) * 1_000_000L
                + createdAt.getNano() / 1_000L;

        // 고정폭으로 패딩해야 Redis의 문자열 역순 정렬이 시간 역순과 일치한다.
        return String.format(Locale.ROOT, "%020d:%d", createdAtMicros, entry.courseId());
    }

    private String courseIdFromRedisMember(String member) {
        int separatorIndex = member.lastIndexOf(':');
        if (separatorIndex < 0) {
            return member;
        }
        return member.substring(separatorIndex + 1);
    }

    private byte[] serialize(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String listMember(long courseId) {
        return String.format(Locale.ROOT, "%019d", courseId);
    }

    private String listRankingKey(String key) {
        return key + ":list";
    }

    private String temporaryRankingKey(String key) {
        return key + ":" + TMP;
    }

    private String officialRankingKey() {
        return RedisKey.of(DOMAIN, RANKING, OFFICIAL);
    }

    private String localRankingKey() {
        return RedisKey.of(DOMAIN, RANKING, LOCAL);
    }
}
