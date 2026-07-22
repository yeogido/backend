package com.yeogido.backend.global.redis;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisKeyTest {

    @Test
    void ofJoinsKeyPartsWithColon() {
        String key = RedisKey.of("course", "1", "views");

        assertThat(key).isEqualTo("course:1:views");
    }

    @Test
    void dailyAddsDateAfterDomain() {
        String key = RedisKey.daily("course", LocalDate.of(2026, 7, 23), "1", "views");

        assertThat(key).isEqualTo("course:20260723:1:views");
    }

    @Test
    void keyPartCannotContainDelimiter() {
        assertThatThrownBy(() -> RedisKey.of("course", "1:views"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
