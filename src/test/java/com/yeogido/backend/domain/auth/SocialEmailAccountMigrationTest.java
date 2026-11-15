package com.yeogido.backend.domain.auth;

import static org.assertj.core.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

@Tag("mysql-migration")
class SocialEmailAccountMigrationTest {
  private Connection server;
  private Connection db;
  private String schema;
  private String url;
  private String username;
  private String password;

  @BeforeEach
  void setUp() throws SQLException {
    String serverUrl = System.getenv("MYSQL_MIGRATION_TEST_URL");
    assertThat(serverUrl).as("Use a disposable MySQL server").isNotBlank();
    username = System.getenv().getOrDefault("MYSQL_MIGRATION_TEST_USER", "root");
    password = System.getenv().getOrDefault("MYSQL_MIGRATION_TEST_PASSWORD", "");
    schema = "social_email_" + UUID.randomUUID().toString().replace("-", "");
    server = DriverManager.getConnection(serverUrl, username, password);
    try (var s = server.createStatement()) { s.execute("CREATE DATABASE " + schema); }
    var parts = serverUrl.split("\\?", 2);
    url = parts[0].replaceAll("/$", "") + "/" + schema + (parts.length == 2 ? "?" + parts[1] : "");
    migrate("50");
    db = DriverManager.getConnection(url, username, password);
  }

  @AfterEach
  void cleanUp() throws SQLException {
    if (db != null) db.close();
    if (server != null) {
      try (var connection = server; var s = connection.createStatement()) {
        s.execute("DROP DATABASE IF EXISTS " + schema);
      }
    }
  }

  @Test
  void backfillsSocialAccountsAndPreservesEmailUniqueness() throws SQLException {
    sql("INSERT INTO region(id,name,type,created_at,updated_at) VALUES(1,'서울','REGION',NOW(),NOW())");
    legacyUser(1, "local@example.com", "EMAIL001");
    legacyUser(2, "shared@example.com", "SOCIAL02");
    // 비밀번호가 있는 과거 소셜 계정도 password 유무가 아닌 연결 정보로 분류한다.
    sql("UPDATE `user` SET password='legacy-hash' WHERE id=2");
    sql("INSERT INTO social_account(user_id,provider,provider_id,created_at,updated_at) "
      + "VALUES(2,'KAKAO','provider-id',NOW(),NOW())");
    migrate("51");
    assertThat(value("SELECT account_type FROM `user` WHERE id=1")).isEqualTo("EMAIL");
    assertThat(value("SELECT account_type FROM `user` WHERE id=2")).isEqualTo("SOCIAL");
    assertThat(value("SELECT email_account_key FROM `user` WHERE id=2")).isNull();
    sql("INSERT INTO `user`(id,nickname,email,account_type,gender,birth_year,region_id,role,status,friend_code,created_at,updated_at) "
      + "VALUES(3,'네이버','shared@example.com','SOCIAL','NONE','2000',1,'USER','ACTIVE','SOCIAL03',NOW(),NOW())");
    sql("INSERT INTO social_account(user_id,provider,provider_id,created_at,updated_at) "
      + "VALUES(3,'NAVER','provider-id',NOW(),NOW())");
    legacyUser(4, "shared@example.com", "EMAIL004");
    assertThatThrownBy(() -> legacyUser(5, "shared@example.com", "EMAIL005"))
      .isInstanceOf(SQLException.class).hasMessageContaining("uk_user_email_account");
    sql("UPDATE `user` SET status='DELETED' WHERE id=4");
    assertThatThrownBy(() -> legacyUser(6, "shared@example.com", "EMAIL006"))
      .isInstanceOf(SQLException.class).hasMessageContaining("uk_user_email_account");
    assertThatThrownBy(() -> sql("INSERT INTO social_account(user_id,provider,provider_id,created_at,updated_at) "
      + "VALUES(3,'KAKAO','provider-id',NOW(),NOW())"))
      .isInstanceOf(SQLException.class).hasMessageContaining("uk_provider_provider_id");
  }

  @Test
  void emptyDatabaseMigratesAndRepeatedMigrationIsNoop() {
    migrate("51");
    assertThat(Flyway.configure().dataSource(url, username, password).target("51").load()
      .migrate().migrationsExecuted).isZero();
  }

  private void legacyUser(int id, String email, String code) throws SQLException {
    sql("INSERT INTO `user`(id,nickname,email,gender,birth_year,region_id,role,status,friend_code,created_at,updated_at) "
      + "VALUES(" + id + ",'회원','" + email + "','NONE','2000',1,'USER','ACTIVE','" + code + "',NOW(),NOW())");
  }

  private void migrate(String target) {
    Flyway.configure().dataSource(url, username, password).target(target).load().migrate();
  }

  private void sql(String query) throws SQLException {
    try (var s = db.createStatement()) { s.execute(query); }
  }

  private String value(String query) throws SQLException {
    try (var s = db.createStatement(); var r = s.executeQuery(query)) {
      assertThat(r.next()).isTrue();
      return r.getString(1);
    }
  }
}
