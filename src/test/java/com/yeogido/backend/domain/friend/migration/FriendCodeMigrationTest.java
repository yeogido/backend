package com.yeogido.backend.domain.friend.migration;

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
class FriendCodeMigrationTest {

    private static final int LEGACY_USER_COUNT = 200;

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
        schema = "friend_migration_" + UUID.randomUUID().toString().replace("-", "");
        server = DriverManager.getConnection(serverUrl, username, password);
        try (var statement = server.createStatement()) {
            statement.execute("CREATE DATABASE " + schema);
        }
        databaseUrl = "jdbc:mysql://" + server.getMetaData().getURL()
                .substring("jdbc:mysql://".length()).split("/", 2)[0] + "/" + schema;
        // 친구코드 도입 직전인 V36까지 적용하여 기존 DB 구조 재현
        Flyway.configure().dataSource(databaseUrl, username, password).target("36").load().migrate();
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
    void assignsUniqueFriendCodeToEveryLegacyUser() throws SQLException {
        execute("""
                INSERT INTO region (id, name, type, created_at, updated_at)
                VALUES (1, '서울', 'REGION', NOW(6), NOW(6))
                """);
        for (int i = 1; i <= LEGACY_USER_COUNT; i++) {
            execute("INSERT INTO `user` (nickname, email, gender, birth_year, region_id, role, status, "
                    + "created_at, updated_at) VALUES ('사용자" + i + "', 'user" + i + "@test.com', "
                    + "'MALE', '2000', 1, 'USER', 'ACTIVE', NOW(6), NOW(6))");
        }

        migrateTo37();

        List<List<Object>> codes = rows("SELECT friend_code FROM `user`");
        assertThat(codes).hasSize(LEGACY_USER_COUNT)
                .allSatisfy(row -> assertThat((String) row.get(0)).matches("[A-HJ-NP-Z2-9]{8}"));
        assertThat(codes.stream().map(row -> row.get(0)).distinct()).hasSize(LEGACY_USER_COUNT);

        assertThatThrownBy(() -> execute("UPDATE `user` SET friend_code = NULL WHERE id = 1"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(
                "UPDATE `user` SET friend_code = (SELECT code FROM (SELECT friend_code AS code "
                        + "FROM `user` WHERE id = 2) source) WHERE id = 1"))
                .isInstanceOf(SQLException.class).hasMessageContaining("uk_user_friend_code");
    }

    @Test
    void migratesEmptyDatabaseAndDoesNotReapply37() {
        migrateTo37();

        assertThat(Flyway.configure().dataSource(databaseUrl, username, password)
                .target("37").load().migrate().migrationsExecuted).isZero();
    }

    private void migrateTo37() {
        assertThat(Flyway.configure().dataSource(databaseUrl, username, password)
                .target("37").load().migrate().migrationsExecuted).isEqualTo(1);
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
