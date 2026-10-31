package com.yeogido.backend.domain.friend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
class FriendshipMigrationTest {

    private Connection server;
    private Connection database;
    private String schema;
    private String databaseUrl;
    private String username;
    private String password;

    @BeforeEach
    void prepareSchema() throws SQLException {
        String serverUrl = System.getenv("MYSQL_MIGRATION_TEST_URL");
        assertThat(serverUrl).as("Set MYSQL_MIGRATION_TEST_URL to a disposable MySQL server URL")
                .isNotBlank();
        username = System.getenv().getOrDefault("MYSQL_MIGRATION_TEST_USER", "root");
        password = System.getenv().getOrDefault("MYSQL_MIGRATION_TEST_PASSWORD", "");
        schema = "friendship_migration_" + UUID.randomUUID().toString().replace("-", "");
        server = DriverManager.getConnection(serverUrl, username, password);
        try (var statement = server.createStatement()) {
            statement.execute("CREATE DATABASE " + schema);
        }
        databaseUrl = withSchema(serverUrl, schema);
        // friendship 도입 직전인 V39까지 적용하여 기존 DB 구조 재현
        Flyway.configure().dataSource(databaseUrl, username, password).target("39").load().migrate();
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
    void createsFriendshipWithPairAndStatusConstraints() throws SQLException {
        migrateTo40();
        execute("""
                INSERT INTO region (id, name, type, created_at, updated_at)
                VALUES (1, '서울', 'REGION', NOW(6), NOW(6))
                """);
        for (int i = 1; i <= 3; i++) {
            execute("INSERT INTO `user` (id, nickname, email, gender, birth_year, region_id, role, status, "
                    + "friend_code, created_at, updated_at) VALUES (" + i + ", '사용자" + i + "', 'user" + i
                    + "@test.com', 'MALE', '2000', 1, 'USER', 'ACTIVE', 'CODE000" + (i + 1) + "', NOW(6), NOW(6))");
        }

        execute(friendshipInsert(2, 1, 1, 2, "PENDING"));

        assertThatThrownBy(() -> execute(friendshipInsert(1, 2, 1, 2, "PENDING")))
                .isInstanceOf(SQLException.class).hasMessageContaining("uk_friendship_pair");
        assertThatThrownBy(() -> execute(friendshipInsert(1, 3, 3, 1, "PENDING")))
                .isInstanceOf(SQLException.class).hasMessageContaining("chk_friendship_pair");
        assertThatThrownBy(() -> execute(friendshipInsert(1, 3, 2, 3, "PENDING")))
                .isInstanceOf(SQLException.class).hasMessageContaining("chk_friendship_pair");
        assertThatThrownBy(() -> execute(friendshipInsert(1, 3, 1, 3, "REJECTED")))
                .isInstanceOf(SQLException.class).hasMessageContaining("chk_friendship_status");
        assertThatThrownBy(() -> execute(friendshipInsert(1, 99, 1, 99, "PENDING")))
                .isInstanceOf(SQLException.class).hasMessageContaining("fk_friendship_receiver");
    }

    @Test
    void doesNotReapply40() {
        migrateTo40();

        assertThat(Flyway.configure().dataSource(databaseUrl, username, password)
                .target("40").load().migrate().migrationsExecuted).isZero();
    }

    private void migrateTo40() {
        assertThat(Flyway.configure().dataSource(databaseUrl, username, password)
                .target("40").load().migrate().migrationsExecuted).isEqualTo(1);
    }

    private String friendshipInsert(long requesterId, long receiverId, long lowId, long highId, String status) {
        return "INSERT INTO friendship (requester_id, receiver_id, user_low_id, user_high_id, status, "
                + "created_at, updated_at) VALUES (" + requesterId + ", " + receiverId + ", " + lowId + ", "
                + highId + ", '" + status + "', NOW(6), NOW(6))";
    }

    private void execute(String sql) throws SQLException {
        try (var statement = database.createStatement()) {
            statement.execute(sql);
        }
    }

    // 서버 주소의 JDBC 옵션(쿼리 문자열)을 유지한 채 DB 이름만 임시 스키마로 지정
    private static String withSchema(String serverUrl, String schema) {
        int queryStart = serverUrl.indexOf('?');
        String base = queryStart < 0 ? serverUrl : serverUrl.substring(0, queryStart);
        String query = queryStart < 0 ? "" : serverUrl.substring(queryStart);
        String hostPart = base.substring("jdbc:mysql://".length()).split("/", 2)[0];
        return "jdbc:mysql://" + hostPart + "/" + schema + query;
    }
}
