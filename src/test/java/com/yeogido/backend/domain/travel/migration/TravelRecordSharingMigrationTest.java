package com.yeogido.backend.domain.travel.migration;

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

/** 기존 migrationTest 실행 방식에 맞춰 테스트마다 별도의 MySQL 스키마를 생성하고 정리합니다. */
@Tag("mysql-migration")
class TravelRecordSharingMigrationTest {

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
    schema = "travel_record_migration_" + UUID.randomUUID().toString().replace("-", "");
    server = DriverManager.getConnection(serverUrl, username, password);
    try (var statement = server.createStatement()) {
      statement.execute("CREATE DATABASE " + schema);
    }
    databaseUrl = "jdbc:mysql://" + server.getMetaData().getURL()
      .substring("jdbc:mysql://".length()).split("/", 2)[0] + "/" + schema;
    Flyway.configure().dataSource(databaseUrl, username, password).target("36").load().migrate();
    database = DriverManager.getConnection(databaseUrl, username, password);
  }

  /** 테스트가 생성한 UUID 스키마와 연결만 정리하며, 준비 도중 실패해도 기존 DB는 건드리지 않습니다. */
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
  void preservesLegacyRecordsPhotosAndStickersAndInitializesPrivateVersionZero() throws SQLException {
    seedUsersAndRecords();
    execute("""
      INSERT INTO sticker (id, name, image_key, sticker_type, category, created_at, updated_at)
      VALUES (1, '하트', 'stickers/heart.png', 'DEFAULT', 'OBJECT', NOW(6), NOW(6))
      """);
    execute("""
      INSERT INTO travel_record_sticker (travel_record_id, sticker_id, position_x, position_y,
        rotation, scale, z_index, created_at, updated_at)
      VALUES (1, 1, 10, 20, 0, 1, 1, NOW(6), NOW(6))
      """);
    var originalRecords = rows("SELECT * FROM travel_record ORDER BY id");
    var originalPhotos = rows("SELECT * FROM travel_record_photo ORDER BY id");
    var originalStickers = rows("SELECT * FROM travel_record_sticker ORDER BY id");

    migrateTo37();

    assertThat(rows("SELECT * FROM travel_record ORDER BY id").stream()
      .map(row -> row.subList(0, row.size() - 2)).toList()).isEqualTo(originalRecords);
    assertThat(rows("SELECT visibility, version FROM travel_record ORDER BY id"))
      .containsExactly(List.of("PRIVATE", 0L), List.of("PRIVATE", 0L));
    assertThat(rows("SELECT * FROM travel_record_photo ORDER BY id")).isEqualTo(originalPhotos);
    assertThat(rows("SELECT * FROM travel_record_sticker ORDER BY id")).isEqualTo(originalStickers);
    assertThat(count("travel_record_member")).isZero();
    assertThat(count("travel_record_invitation")).isZero();

    assertThatThrownBy(() -> execute("UPDATE travel_record SET visibility = 'UNKNOWN' WHERE id = 1"))
      .isInstanceOf(SQLException.class).hasMessageContaining("chk_travel_record_visibility");
    assertThatThrownBy(() -> execute("UPDATE travel_record SET visibility = NULL WHERE id = 1"))
      .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> execute("UPDATE travel_record SET version = NULL WHERE id = 1"))
      .isInstanceOf(SQLException.class);
  }

  @Test
  void enforcesUniqueStateAndForeignKeyConstraints() throws SQLException {
    seedUsersAndRecords();
    migrateTo37();
    execute("UPDATE travel_record SET visibility = 'FRIENDS' WHERE id = 1");
    execute("UPDATE travel_record SET visibility = 'PUBLIC' WHERE id = 2");
    insertMember(1, 2);
    insertInvitation(1, 2, 3);

    assertThat(rows("SELECT status FROM travel_record_invitation"))
      .containsExactly(List.of("PENDING"));
    assertThatThrownBy(() -> insertMember(1, 2))
      .isInstanceOf(SQLException.class).hasMessageContaining("uk_travel_record_member_record_user");
    assertThatThrownBy(() -> insertInvitation(1, 1, 3))
      .isInstanceOf(SQLException.class).hasMessageContaining("uk_travel_record_invitation_record_invitee");
    execute("UPDATE travel_record_invitation SET status = 'ACCEPTED'");
    execute("UPDATE travel_record_invitation SET status = 'DECLINED'");
    assertThatThrownBy(() -> execute("UPDATE travel_record_invitation SET status = 'UNKNOWN'"))
      .isInstanceOf(SQLException.class).hasMessageContaining("chk_travel_record_invitation_status");
    assertThatThrownBy(() -> execute("UPDATE travel_record_invitation SET status = NULL"))
      .isInstanceOf(SQLException.class);

    assertThatThrownBy(() -> insertMember(999, 2))
      .isInstanceOf(SQLException.class).hasMessageContaining("fk_travel_record_member_record");
    assertThatThrownBy(() -> insertMember(1, 999))
      .isInstanceOf(SQLException.class).hasMessageContaining("fk_travel_record_member_user");
    assertThatThrownBy(() -> insertInvitation(999, 1, 3))
      .isInstanceOf(SQLException.class).hasMessageContaining("fk_travel_record_invitation_record");
    assertThatThrownBy(() -> insertInvitation(1, 999, 2))
      .isInstanceOf(SQLException.class).hasMessageContaining("fk_travel_record_invitation_inviter");
    assertThatThrownBy(() -> insertInvitation(1, 1, 999))
      .isInstanceOf(SQLException.class).hasMessageContaining("fk_travel_record_invitation_invitee");
    assertThat(rows("""
      SELECT constraint_name, delete_rule FROM information_schema.referential_constraints
      WHERE constraint_schema = DATABASE()
        AND table_name IN ('travel_record_member', 'travel_record_invitation')
      ORDER BY constraint_name
      """)).containsExactly(
      List.of("fk_travel_record_invitation_invitee", "RESTRICT"),
      List.of("fk_travel_record_invitation_inviter", "RESTRICT"),
      List.of("fk_travel_record_invitation_record", "CASCADE"),
      List.of("fk_travel_record_member_record", "CASCADE"),
      List.of("fk_travel_record_member_user", "RESTRICT"));
  }

  @Test
  void deletingMemberPreservesContentAndDeletingAlbumCascadesOnlyItsRelations() throws SQLException {
    seedUsersAndRecords();
    migrateTo37();
    insertMember(1, 2);
    insertMember(1, 3);
    insertMember(2, 3);
    insertInvitation(1, 2, 3);
    insertInvitation(2, 2, 1);
    var originalPhotos = rows("SELECT * FROM travel_record_photo ORDER BY id");

    execute("DELETE FROM travel_record_member WHERE travel_record_id = 1 AND user_id = 2");

    assertThat(count("travel_record")).isEqualTo(2);
    assertThat(count("travel_record_member")).isEqualTo(2);
    assertThat(rows("SELECT * FROM travel_record_photo ORDER BY id")).isEqualTo(originalPhotos);
    assertThatThrownBy(() -> execute("DELETE FROM `user` WHERE id = 3"))
      .isInstanceOf(SQLException.class);

    // 사진은 기존 서비스에서 먼저 정리하고, 새 관계 데이터는 앨범 FK의 CASCADE로 정리
    execute("DELETE FROM travel_record_photo WHERE travel_record_id = 1");
    execute("DELETE FROM travel_record WHERE id = 1");

    assertThat(rows("SELECT id, user_id FROM travel_record")).containsExactly(List.of(2L, 2L));
    assertThat(rows("SELECT travel_record_id, user_id FROM travel_record_member"))
      .containsExactly(List.of(2L, 3L));
    assertThat(rows("SELECT travel_record_id FROM travel_record_invitation"))
      .containsExactly(List.of(2L));
    assertThat(rows("SELECT * FROM travel_record_photo ORDER BY id"))
      .containsExactly(originalPhotos.get(1));
    assertThat(count("`user`")).isEqualTo(3);
  }

  @Test
  void migratesEmptyDatabaseAndDoesNotReapply37() {
    migrateTo37();

    assertThat(Flyway.configure().dataSource(databaseUrl, username, password)
      .target("37").load().migrate().migrationsExecuted).isZero();
  }

  /** V37 적용 전 상태에서 서로 다른 생성자의 기록과 사진을 준비하여 데이터 보존을 비교합니다. */
  private void seedUsersAndRecords() throws SQLException {
    execute("""
      INSERT INTO region (id, name, type, created_at, updated_at)
      VALUES (1, '부산', 'REGION', NOW(6), NOW(6))
      """);
    for (int id = 1; id <= 3; id++) {
      execute("INSERT INTO `user` (id, nickname, email, gender, birth_year, region_id, role, status, "
        + "created_at, updated_at) VALUES (" + id + ", '사용자', 'sharing-" + id
        + "@test.com', 'FEMALE', '2000', 1, 'USER', 'ACTIVE', NOW(6), NOW(6))");
    }
    for (int id = 1; id <= 2; id++) {
      execute("INSERT INTO travel_record (id, user_id, region_id, title, start_date, end_date, "
        + "cover_image_key, folder_theme, created_at, updated_at) VALUES (" + id + ", " + id
        + ", 1, '기존 기록', '2026-10-01', '2026-10-03', 'legacy.jpg', 'BASIC', NOW(6), NOW(6))");
      execute("INSERT INTO travel_record_photo (travel_record_id, image_key, image_order, created_at, "
        + "updated_at) VALUES (" + id + ", 'legacy.jpg', 1, NOW(6), NOW(6))");
    }
  }

  private void insertMember(long recordId, long userId) throws SQLException {
    execute("INSERT INTO travel_record_member (travel_record_id, user_id, created_at, updated_at) "
      + "VALUES (" + recordId + ", " + userId + ", NOW(6), NOW(6))");
  }

  private void insertInvitation(long recordId, long inviterId, long inviteeId) throws SQLException {
    execute("INSERT INTO travel_record_invitation (travel_record_id, inviter_user_id, invitee_user_id, "
      + "created_at, updated_at) VALUES (" + recordId + ", " + inviterId + ", " + inviteeId
      + ", NOW(6), NOW(6))");
  }

  private void migrateTo37() {
    assertThat(Flyway.configure().dataSource(databaseUrl, username, password)
      .target("37").load().migrate().migrationsExecuted).isEqualTo(1);
  }

  private long count(String table) throws SQLException {
    try (var statement = database.createStatement();
         var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      result.next();
      return result.getLong(1);
    }
  }

  private void execute(String sql) throws SQLException {
    try (var statement = database.createStatement()) {
      statement.execute(sql);
    }
  }

  /** NULL을 포함한 조회 결과 전체를 값의 순서대로 보관하여 마이그레이션 전후 데이터를 비교합니다. */
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
