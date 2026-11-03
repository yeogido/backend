package com.yeogido.backend.domain.course.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * MYSQL_MIGRATION_TEST_URL에 DB 이름을 제외한 테스트용 MySQL 서버 주소 설정 필수
 * ./gradlew migrationTest로 실행하며 테스트마다 별도 스키마 생성 및 정리
 */
@Tag("mysql-migration")
class CourseDailyScheduleMigrationTest {

    private Connection server;
    private Connection database;
    private String schema;
    private String databaseUrl;
    private String username;
    private String password;

    @BeforeEach
    void prepareLegacySchema() throws SQLException {
        String serverUrl = System.getenv("MYSQL_MIGRATION_TEST_URL");
        assertThat(serverUrl).as("Set MYSQL_MIGRATION_TEST_URL to a disposable MySQL server URL")
                .isNotBlank();
        username = System.getenv().getOrDefault("MYSQL_MIGRATION_TEST_USER", "root");
        password = System.getenv().getOrDefault("MYSQL_MIGRATION_TEST_PASSWORD", "");
        schema = "course_migration_" + UUID.randomUUID().toString().replace("-", "");
        server = DriverManager.getConnection(serverUrl, username, password);
        try (var statement = server.createStatement()) {
            statement.execute("CREATE DATABASE " + schema);
        }
        databaseUrl = "jdbc:mysql://" + server.getMetaData().getURL()
                .substring("jdbc:mysql://".length()).split("/", 2)[0] + "/" + schema;
        // 일차 도입 직전인 V35까지 적용하여 기존 DB 구조 재현
        Flyway.configure().dataSource(databaseUrl, username, password).target("35").load().migrate();
        database = DriverManager.getConnection(databaseUrl, username, password);
    }

    @AfterEach
    void removeOnlyTestSchema() throws SQLException {
        if (database != null) {
            database.close();
        }
        if (server != null) {
            try (Connection connection = server; var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + schema);
            }
        }
    }

    @Test
    void migratesLegacyDataWithoutChangingItemsOrTravelTimes() throws SQLException {
        execute("""
                INSERT INTO region (id, name, type, created_at, updated_at)
                VALUES (1, '부산', 'REGION', NOW(6), NOW(6))
                """);
        execute("""
                INSERT INTO place (id, region_id, external_place_id, source, name,
                    latitude, longitude, created_at, updated_at)
                VALUES (1, 1, 'legacy-place', 'KAKAO', '기존 장소', 35.1, 129.1, NOW(6), NOW(6))
                """);
        execute("""
                INSERT INTO content (id, place_id, source, title, created_at, updated_at)
                VALUES (1, 1, 'TOUR_API', '기존 행사', NOW(6), NOW(6))
                """);
        for (int i = 0; i < 5; i++) {
            String duration = new String[]{"'DAY_TRIP'", "'ONE_NIGHT'", "'TWO_NIGHT'", "'THREE_PLUS'", "NULL"}[i];
            execute("INSERT INTO course (id, region_id, title, course_type, duration_type, created_at, updated_at) "
                    + "VALUES (" + (i + 1) + ", 1, '기존 코스', 'OFFICIAL', " + duration + ", NOW(6), NOW(6))");
        }
        execute("UPDATE course SET deleted_at = NOW(6) WHERE id = 5");
        execute("""
                INSERT INTO course_item (id, course_id, place_id, content_id, item_type, order_no,
                    image_key, created_at, updated_at) VALUES
                    (1, 4, 1, NULL, 'PLACE', 1, 'legacy.jpg', NOW(6), NOW(6)),
                    (2, 4, NULL, 1, 'CONTENT', 2, NULL, NOW(6), NOW(6)),
                    (3, 2, 1, NULL, 'PLACE', 1, NULL, NOW(6), NOW(6))
                """);
        execute("""
                INSERT INTO course_item_time (from_course_item_id, to_course_item_id, transport_mode,
                    duration_minutes, created_at, updated_at) VALUES
                    (1, 2, 'WALK', 30, NOW(6), NOW(6)), (1, 2, 'CAR', 10, NOW(6), NOW(6))
                """);
        // 새 컬럼 추가 전의 원본 데이터 보존 여부 비교를 위한 스냅샷 확보
        var originalCourses = rows("SELECT * FROM course ORDER BY id");
        var originalItems = rows("SELECT * FROM course_item ORDER BY id");
        var originalTimes = rows("SELECT * FROM course_item_time ORDER BY id");

        migrateTo36();

        assertThat(rows("SELECT total_days FROM course ORDER BY id"))
                .containsExactly(List.of(1), List.of(2), List.of(3), List.of(4), List.of(1));
        // 마지막에 추가된 일수·일차 컬럼을 제외하고 기존 컬럼 전체 비교
        assertThat(rows("SELECT * FROM course ORDER BY id").stream()
                .map(row -> row.subList(0, row.size() - 1)).toList()).isEqualTo(originalCourses);
        assertThat(rows("SELECT * FROM course_item ORDER BY id").stream()
                .map(row -> row.subList(0, row.size() - 1)).toList()).isEqualTo(originalItems);
        assertThat(rows("SELECT day_no FROM course_item ORDER BY id"))
                .containsExactly(List.of(1), List.of(1), List.of(1));
        assertThat(rows("SELECT * FROM course_item_time ORDER BY id")).isEqualTo(originalTimes);

        execute("""
                INSERT INTO course_item (course_id, place_id, item_type, day_no, order_no, created_at, updated_at)
                VALUES (4, 1, 'PLACE', 2, 1, NOW(6), NOW(6))
                """);
        assertThatThrownBy(() -> execute("""
                INSERT INTO course_item (course_id, place_id, item_type, day_no, order_no, created_at, updated_at)
                VALUES (4, 1, 'PLACE', 2, 1, NOW(6), NOW(6))
                """)).isInstanceOf(SQLException.class).hasMessageContaining("uk_course_item_day_order");
        assertThatThrownBy(() -> execute("UPDATE course SET total_days = 0 WHERE id = 1"))
                .isInstanceOf(SQLException.class).hasMessageContaining("chk_course_total_days");
        assertThatThrownBy(() -> execute("UPDATE course_item SET day_no = 0 WHERE id = 1"))
                .isInstanceOf(SQLException.class).hasMessageContaining("chk_course_item_day");
        assertThatThrownBy(() -> execute("UPDATE course SET total_days = NULL WHERE id = 1"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE course_item SET day_no = NULL WHERE id = 1"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE course_item SET course_id = 999 WHERE id = 1"))
                .isInstanceOf(SQLException.class).hasMessageContaining("fk_course_item_course");
    }

    @Test
    void migratesEmptyDatabaseAndDoesNotReapply36() {
        migrateTo36();

        assertThat(Flyway.configure().dataSource(databaseUrl, username, password)
                .target("36").load().migrate().migrationsExecuted).isZero();
    }

    private void migrateTo36() {
        assertThat(Flyway.configure().dataSource(databaseUrl, username, password)
                .target("36").load().migrate().migrationsExecuted).isEqualTo(1);
    }

    private void execute(String sql) throws SQLException {
        try (var statement = database.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<List<Object>> rows(String sql) throws SQLException {
        try (var statement = database.createStatement(); var result = statement.executeQuery(sql)) {
            List<List<Object>> rows = new ArrayList<>();
            while (result.next()) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
                    row.add(result.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }
}
