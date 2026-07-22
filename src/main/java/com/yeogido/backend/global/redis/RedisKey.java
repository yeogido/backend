package com.yeogido.backend.global.redis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;

public final class RedisKey {

    private static final String DELIMITER = ":";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private RedisKey() {
    }

    public static String of(String domain, String... parts) {
        return join(domain, parts);
    }

    public static String daily(String domain, LocalDate date, String... parts) {
        Objects.requireNonNull(date, "date must not be null");

        String[] dateParts = new String[parts.length + 1];
        dateParts[0] = DATE_FORMATTER.format(date);
        System.arraycopy(parts, 0, dateParts, 1, parts.length);

        return join(domain, dateParts);
    }

    private static String join(String domain, String... parts) {
        Objects.requireNonNull(domain, "domain must not be null");

        String[] keyParts = new String[parts.length + 1];
        keyParts[0] = domain;
        System.arraycopy(parts, 0, keyParts, 1, parts.length);

        return String.join(
                DELIMITER,
                Arrays.stream(keyParts)
                        .map(RedisKey::validatePart)
                        .toList()
        );
    }

    private static String validatePart(String part) {
        if (part == null || part.isBlank()) {
            throw new IllegalArgumentException("Redis key part must not be blank");
        }

        if (part.contains(DELIMITER)) {
            throw new IllegalArgumentException("Redis key part must not contain ':'");
        }

        return part;
    }
}
