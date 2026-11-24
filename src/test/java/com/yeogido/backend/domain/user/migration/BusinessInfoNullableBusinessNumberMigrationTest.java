package com.yeogido.backend.domain.user.migration;

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
 * V53: 해제(REVOKED)된 사업자 정보의 사업자번호를 비울 수 있도록 business_number 를 NULL 허용으로 바꾼다.
 * MYSQL_MIGRATION_TEST_URL에 DB 이름을 제외한 테스트용 MySQL 서버 주소 설정 필수
 * ./gradlew migrationTest로 실행하며 테스트마다 별도 스키마 생성 및 정리
 */
@Tag("mysql-migration")
class BusinessInfoNullableBusinessNumberMigrationTest {

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
        schema = "business_info_migration_" + UUID.randomUUID().toString().replace("-", "");
        server = DriverManager.getConnection(serverUrl, username, password);
        try (var statement = server.createStatement()) {
            statement.execute("CREATE DATABASE " + schema);
        }
        databaseUrl = withSchema(serverUrl, schema);
        // V53 직전인 V52까지 적용하여 기존 DB 구조 재현
        Flyway.configure().dataSource(databaseUrl, username, password).target("52").load().migrate();
        database = DriverManager.getConnection(databaseUrl, username, password);
        insertUser();
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
    void allowsNullBusinessNumberWhileKeepingFormatAndUniqueConstraints() throws SQLException {
        // V52 까지는 사업자번호를 비울 수 없다.
        assertThatThrownBy(() -> execute(businessInfoInsert("NULL")))
                .isInstanceOf(SQLException.class).hasMessageContaining("business_number");

        migrateTo53();

        // 해제된 행 여러 건이 번호 없이 공존할 수 있다(unique 는 NULL 을 여러 건 허용).
        execute(businessInfoInsert("NULL"));
        execute(businessInfoInsert("NULL"));
        execute(businessInfoInsert("'1234567890'"));

        // 형식 검사와 중복 금지는 그대로 유지된다.
        assertThatThrownBy(() -> execute(businessInfoInsert("'12345'")))
                .isInstanceOf(SQLException.class).hasMessageContaining("chk_business_info_business_number");
        assertThatThrownBy(() -> execute(businessInfoInsert("'1234567890'")))
                .isInstanceOf(SQLException.class).hasMessageContaining("uk_business_info_business_number");

        // 해제 처리 자체(번호와 장소 비우기)가 통과한다.
        execute("UPDATE business_info SET business_number = NULL, place_id = NULL, "
                + "verification_status = 'REVOKED' WHERE business_number = '1234567890'");
        try (var statement = database.createStatement();
             var result = statement.executeQuery(
                     "SELECT COUNT(*) FROM business_info WHERE business_number IS NULL")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(3);
        }
    }

    @Test
    void doesNotReapply53() {
        migrateTo53();

        assertThat(Flyway.configure().dataSource(databaseUrl, username, password)
                .target("53").load().migrate().migrationsExecuted).isZero();
    }

    private void migrateTo53() {
        assertThat(Flyway.configure().dataSource(databaseUrl, username, password)
                .target("53").load().migrate().migrationsExecuted).isEqualTo(1);
    }

    private void insertUser() throws SQLException {
        execute("""
                INSERT INTO region (id, name, type, created_at, updated_at)
                VALUES (1, '서울', 'REGION', NOW(6), NOW(6))
                """);
        execute("""
                INSERT INTO `user` (id, nickname, email, gender, birth_year, region_id, role, status,
                                    friend_code, created_at, updated_at)
                VALUES (1, '사장님', 'owner@test.com', 'MALE', '2000', 1, 'BUSINESS', 'ACTIVE',
                        'CODE0001', NOW(6), NOW(6))
                """);
    }

    private String businessInfoInsert(String businessNumberLiteral) {
        return "INSERT INTO business_info (user_id, business_number, opening_date, representative_name, "
                + "registration_image_key, business_name, business_address, verification_status, verified_at, "
                + "created_at, updated_at) VALUES (1, " + businessNumberLiteral + ", '2020-01-01', '홍길동', "
                + "'businesses/certificate.jpg', '카페', '부산 수영구', 'APPROVED', NOW(6), NOW(6), NOW(6))";
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
